package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.support.ReplicatedBirthDates;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roles Objetivo con peticiones simultáneas y repetidas contra PostgreSQL real.
 *
 * <p>Sin {@code @Transactional} en la clase: cada hilo necesita su propia transacción, como cada petición HTTP.
 * Los perfiles de prueba usan identidades {@code uid-roles-} y se borran al terminar.</p>
 */
@SpringBootTest
@Testcontainers
class TargetRoleConcurrencyIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final long TIMEOUT_SECONDS = 30;
    private static final String ROLE_PREFIX = "a0000001-0000-0000-0000-00000000000";

    @Autowired
    ProfileAppService profileAppService;

    @Autowired
    ProfileCreationAppService profileCreationAppService;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbc.update("delete from perfil_profesional where firebase_uid like 'uid-roles-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbc, "uid-roles-");
    }

    /** Identidad nueva con su fecha de nacimiento replicada, como la dejaría el evento de cuenta creada. */
    private String newUid() {
        var uid = "uid-roles-" + UUID.randomUUID();
        ReplicatedBirthDates.insertAdult(jdbc, uid);
        return uid;
    }

    @Test
    @DisplayName("El mismo rol enviado dos veces a la vez se guarda una sola vez y la segunda recibe el duplicado")
    void addTargetRole_shouldStoreOnce_whenSameRoleArrivesTwiceAtOnce() throws Exception {
        var uid = newUid();
        var profileId = profileCreationAppService.createProfile(new CreateProfileCommand(uid)).getId().value();

        var outcomes = runTogether(2, i -> roleId(2), uid, profileId);

        assertThat(outcomes).filteredOn(o -> o instanceof DuplicateTargetRoleException).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !(o instanceof Exception)).hasSize(1);
        assertThat(countRoles(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Seis roles distintos a la vez dejan cinco y el sexto recibe el máximo")
    void addTargetRole_shouldNeverExceedFive_whenSixArriveAtOnce() throws Exception {
        var uid = newUid();
        var profileId = profileCreationAppService.createProfile(new CreateProfileCommand(uid)).getId().value();

        var outcomes = runTogether(6, i -> roleId(i + 1), uid, profileId);

        assertThat(outcomes).filteredOn(o -> o instanceof MaxTargetRolesExceededException).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !(o instanceof Exception)).hasSize(5);
        assertThat(countRoles(profileId)).isEqualTo(5);
    }

    @Test
    @DisplayName("El mismo rol enviado veinte veces seguidas se guarda una vez y las otras diecinueve reciben el duplicado")
    void addTargetRole_shouldStoreOnce_whenSameRoleIsSentTwentyTimes() {
        var uid = newUid();
        var profileId = profileCreationAppService.createProfile(new CreateProfileCommand(uid)).getId().value();
        var duplicates = 0;

        for (int i = 0; i < 20; i++) {
            try {
                profileAppService.addTargetRole(new AddTargetRoleCommand(profileId, uid, UUID.fromString(roleId(2))));
            } catch (DuplicateTargetRoleException e) {
                duplicates++;
            }
        }

        assertThat(duplicates).isEqualTo(19);
        assertThat(countRoles(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos eliminaciones a la vez en un perfil activo con dos roles dejan uno: la segunda recibe el último rol")
    void removeTargetRole_shouldKeepOneRole_whenTwoRemovalsArriveOnActiveProfile() throws Exception {
        var uid = newUid();
        var profileId = profileCreationAppService.createProfile(new CreateProfileCommand(uid)).getId().value();
        profileAppService.updateProfileInfo(new UpdateProfileInfoCommand(
                profileId, uid, "Perfil de prueba", "Resumen de prueba con más de diez caracteres.", null));
        profileAppService.addEducation(new AddEducationCommand(profileId, uid, "Universidad de prueba", "Ingeniería de Sistemas",
                "UNDERGRADUATE", "2018-01", "2022-12", false));
        profileAppService.addSkill(new AddSkillCommand(profileId, uid, "Java", "ADVANCED", "MANUAL"));
        profileAppService.addTargetRole(new AddTargetRoleCommand(profileId, uid, UUID.fromString(roleId(1))));
        var withTwo = profileAppService.addTargetRole(new AddTargetRoleCommand(profileId, uid, UUID.fromString(roleId(2))));
        profileAppService.completeProfile(profileId, uid);
        var roleIds = withTwo.getTargetRoles().stream().map(role -> role.getId()).toList();

        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Object>>();
        for (var roleId : roleIds) {
            futures.add(executor.submit(() -> {
                start.await();
                return profileAppService.removeTargetRole(profileId, uid, roleId);
            }));
        }
        start.countDown();
        var outcomes = new ArrayList<Object>();
        for (var future : futures) {
            try {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                outcomes.add(e.getCause());
            }
        }

        assertThat(outcomes).filteredOn(o -> o instanceof LastTargetRoleException).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !(o instanceof Exception)).hasSize(1);
        assertThat(countRoles(profileId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from perfil_profesional where id = ?", String.class, profileId))
                .isEqualTo(ProfileStatus.COMPLETED.name());
    }

    private static String roleId(int number) {
        return ROLE_PREFIX + number;
    }

    private int countRoles(UUID profileId) {
        return jdbc.queryForObject("select count(*) from rol_objetivo where perfil_id = ?", Integer.class, profileId);
    }

    /**
     * Lanza una petición por hilo y las suelta a la vez. Cada resultado es el perfil devuelto o la excepción de
     * negocio que lanzó la operación.
     */
    private List<Object> runTogether(int requests, IntFunction<String> roleOf, String uid, UUID profileId) throws Exception {
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Object>>();
        for (int i = 0; i < requests; i++) {
            var role = UUID.fromString(roleOf.apply(i));
            futures.add(executor.submit(() -> {
                start.await();
                return profileAppService.addTargetRole(new AddTargetRoleCommand(profileId, uid, role));
            }));
        }
        start.countDown();
        var outcomes = new ArrayList<Object>();
        for (var future : futures) {
            try {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                outcomes.add(e.getCause());
            }
        }
        return outcomes;
    }
}
