package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import co.edu.unicauca.cameia.perfil.support.ReplicatedBirthDates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Una escritura de un perfil activo no debe pedir una segunda conexión a la base para publicar su evento: con un pool
 * pequeño y muchas escrituras a la vez, cada petición retendría su conexión esperando otra y todas esperarían el plazo
 * completo antes de fallar. Aquí el pool tiene el tamaño de las escrituras simultáneas y el plazo de espera de una
 * conexión es de 2 s, así que una espera por una segunda conexión se nota como una escritura lenta.
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=4",
        "spring.datasource.hikari.connection-timeout=2000"
})
class ProfileEventsSmallPoolIT extends MessagingIntegrationSupport {

    private static final String QUEUE = RabbitConfig.INTERVIEW_PROFILE_QUEUE;
    private static final UUID BACKEND_ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final int WRITERS = 4;
    private static final Duration FAST = Duration.ofMillis(1500);

    @Autowired
    ProfileCreationAppService creation;
    @Autowired
    ProfileAppService profiles;

    @BeforeEach
    void cleanState() {
        resetState();
        jdbcTemplate.update("delete from evento_saliente");
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    @AfterEach
    void restore() {
        jdbcTemplate.update("delete from evento_saliente");
        jdbcTemplate.update("delete from perfil_profesional where firebase_uid like 'uid-pool-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbcTemplate, "uid-pool-");
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    /** Crea y finaliza un perfil; devuelve su id y su dueño. */
    private record ActiveProfile(UUID id, String uid) {
    }

    private ActiveProfile activeProfile() {
        var uid = "uid-pool-" + UUID.randomUUID();
        ReplicatedBirthDates.insertAdult(jdbcTemplate, uid);
        var id = creation.createProfile(new CreateProfileCommand(uid)).getId().value();
        profiles.updateProfileInfo(new UpdateProfileInfoCommand(id, uid, "Analista de datos",
                "Analista con tres años de experiencia en SQL y Python.", null));
        profiles.addEducation(new AddEducationCommand(id, uid, "Universidad del Cauca", "Ingeniería de Sistemas",
                "UNDERGRADUATE", "2017-02", "2021-12", false));
        profiles.addSkill(new AddSkillCommand(id, uid, "Python", "INTERMEDIATE"));
        profiles.addTargetRole(new AddTargetRoleCommand(id, uid, BACKEND_ROLE));
        profiles.completeProfile(id, uid);
        return new ActiveProfile(id, uid);
    }

    private int pendingEvents() {
        return jdbcTemplate.queryForObject("select count(*) from evento_saliente where fecha_publicacion is null",
                Integer.class);
    }

    @Test
    @DisplayName("Escrituras simultáneas de perfiles activos publican su evento sin esperar una segunda conexión")
    void write_shouldPublishWithoutWaiting_whenWritersFillThePool() throws Exception {
        var profilesToWrite = new ArrayList<ActiveProfile>();
        for (int i = 0; i < WRITERS; i++) {
            profilesToWrite.add(activeProfile());
        }
        // Las finalizaciones ya publicaron: se parte de cero para contar solo lo que publican las escrituras.
        jdbcTemplate.update("delete from evento_saliente");
        rabbitAdmin.purgeQueue(QUEUE, false);

        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(WRITERS);
        try {
            List<Future<Duration>> timings = new ArrayList<>();
            for (var profile : profilesToWrite) {
                timings.add(executor.submit(() -> {
                    start.await();
                    long began = System.nanoTime();
                    profiles.addSkill(new AddSkillCommand(profile.id(), profile.uid(), "SQL", "BASIC"));
                    return Duration.ofNanos(System.nanoTime() - began);
                }));
            }
            start.countDown();
            for (var timing : timings) {
                assertThat(timing.get(30, TimeUnit.SECONDS)).isLessThan(FAST);
            }
        } finally {
            executor.shutdownNow();
        }

        // El intento posterior al commit corre en otro hilo: se espera a que anote su resultado.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(pendingEvents()).isZero());
        assertThat(rabbitAdmin.getQueueInfo(QUEUE).getMessageCount()).isEqualTo(WRITERS);
    }
}
