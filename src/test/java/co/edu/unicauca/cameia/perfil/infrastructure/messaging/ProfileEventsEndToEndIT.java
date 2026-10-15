package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.OutboxRelayAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotAllowedException;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import co.edu.unicauca.cameia.perfil.support.ReplicatedBirthDates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * El evento del perfil de punta a punta contra PostgreSQL y RabbitMQ reales: cuándo se publica, qué lleva, qué pasa si el
 * broker rechaza el mensaje y que nada sale de una escritura rechazada o revertida.
 *
 * <p>Los usuarios de prueba empiezan con {@code uid-ev-} y se borran al terminar.</p>
 */
class ProfileEventsEndToEndIT extends MessagingIntegrationSupport {

    private static final String QUEUE = RabbitConfig.INTERVIEW_PROFILE_QUEUE;
    private static final UUID BACKEND_ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    ProfileCreationAppService creation;
    @Autowired
    ProfileAppService profiles;
    @Autowired
    OutboxRelayAppService relay;
    @Autowired
    Queue interviewProfileQueue;

    private final List<String> triggers = new ArrayList<>();
    private final List<String> eventTriggers = new ArrayList<>();

    @BeforeEach
    void cleanState() {
        resetState();
        jdbcTemplate.update("delete from evento_saliente");
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    @AfterEach
    void restore() {
        // Una prueba puede haber borrado el exchange: se vuelve a declarar para las clases que comparten el contexto.
        rabbitAdmin.initialize();
        for (var suffix : triggers) {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_completion_" + suffix + " ON perfil_profesional");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_completion_" + suffix + "()");
        }
        triggers.clear();
        for (var suffix : eventTriggers) {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_event_" + suffix + " ON evento_saliente");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_event_" + suffix + "()");
        }
        eventTriggers.clear();
        jdbcTemplate.update("delete from evento_saliente");
        jdbcTemplate.update("delete from perfil_profesional where firebase_uid like 'uid-ev-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbcTemplate, "uid-ev-");
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    // ── ayudantes ──────────────────────────────────────────────────────────

    private static String newUid() {
        return "uid-ev-" + UUID.randomUUID();
    }

    /** Crea un perfil con los cinco requisitos cubiertos, sin finalizarlo. */
    private UUID draftProfile(String uid) {
        ReplicatedBirthDates.insertAdult(jdbcTemplate, uid);
        var id = creation.createProfile(new CreateProfileCommand(uid)).getId().value();
        profiles.updateProfileInfo(new UpdateProfileInfoCommand(id, uid, "Analista de datos",
                "Analista con tres años de experiencia en SQL y Python.", null));
        profiles.addEducation(new AddEducationCommand(id, uid, "Universidad del Cauca", "Ingeniería de Sistemas",
                "UNDERGRADUATE", "2017-02", "2021-12", false));
        profiles.addSkill(new AddSkillCommand(id, uid, "Python", "INTERMEDIATE"));
        profiles.addTargetRole(new AddTargetRoleCommand(id, uid, BACKEND_ROLE));
        return id;
    }

    /** Crea y finaliza un perfil, y deja la cola vacía para que la prueba parta de cero. */
    private UUID activeProfile(String uid) {
        var id = draftProfile(uid);
        profiles.completeProfile(id, uid);
        assertThat(next()).isNotNull();
        return id;
    }

    private Message next() {
        return rabbitTemplate.receive(QUEUE, 5000);
    }

    private Message nothing() {
        return rabbitTemplate.receive(QUEUE, 1000);
    }

    private static JsonNode body(Message message) {
        return JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    private int outboxRows(UUID profileId) {
        return jdbcTemplate.queryForObject("select count(*) from evento_saliente where agregado_id = ?", Integer.class, profileId);
    }

    /** Espera a que el intento posterior al commit, que corre en otro hilo, deje anotado su resultado en la fila. */
    private void awaitOutboxRow(UUID profileId, String condition) {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbcTemplate.queryForObject(
                "select count(*) from evento_saliente where agregado_id = ? and " + condition, Integer.class, profileId) == 1);
    }

    private String status(UUID profileId) {
        return jdbcTemplate.queryForObject("select estado from perfil_profesional where id = ?", String.class, profileId);
    }

    // ── topología ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("La cola de Entrevista queda ligada a perfil.events con los argumentos acordados")
    void topology_shouldRouteToInterviewQueue_whenServiceStarts() {
        assertThat(interviewProfileQueue.getArguments()).isEqualTo(Map.of(
                "x-dead-letter-exchange", "entrevista.dlx",
                "x-dead-letter-routing-key", "entrevista.perfil-profesional-actualizado"));

        rabbitTemplate.send(RabbitConfig.EXCHANGE_PERFIL, RabbitConfig.PROFILE_UPDATED_KEY,
                MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build());

        assertThat(next()).isNotNull();
    }

    // ── qué se publica ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Al finalizar se publica un mensaje con las propiedades y la carga del contrato, sin embedding")
    void completeProfile_shouldPublishSchemaPayload_whenProfileIsActivated() {
        var uid = newUid();
        var id = draftProfile(uid);
        assertThat(outboxRows(id)).isZero();

        profiles.completeProfile(id, uid);

        var message = next();
        assertThat(message).isNotNull();
        awaitOutboxRow(id, "fecha_publicacion is not null");
        var row = jdbcTemplate.queryForMap("select id, fecha_publicacion, carga from evento_saliente where agregado_id = ?", id);
        var properties = message.getMessageProperties();
        assertThat(properties.getType()).isEqualTo("perfil.profesional-actualizado");
        assertThat(properties.getAppId()).isEqualTo("cameia-perfil");
        assertThat(properties.<Integer>getHeader("x-event-version")).isEqualTo(1);
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getMessageId()).isEqualTo(row.get("id").toString());
        var json = body(message);
        assertThat(json.get("usuarioId").asString()).isEqualTo(uid);
        assertThat(json.get("contextoProfesional").get("id").asString()).isEqualTo(id.toString());
        assertThat(json.get("contextoProfesional").get("estado").asString()).isEqualTo("COMPLETED");
        assertThat(json.get("contextoProfesional").get("fecha_archivado").isNull()).isTrue();
        assertThat(json.get("contextoProfesional").get("experiencias")).isEmpty();
        assertThat(json.get("contextoProfesional").get("habilidades").get(0).toString())
                .isEqualTo("{\"nombre\":\"Python\",\"nivel\":\"INTERMEDIATE\"}");
        assertThat(json.get("rolesAspirados").get(0).get("codigo").asString()).isEqualTo(BACKEND_ROLE.toString());
        assertThat(json.has("embedding")).isFalse();
        assertThat(row.get("fecha_publicacion")).isNotNull();
        assertThat(row.get("carga")).isNull();
    }

    @Test
    @DisplayName("Un perfil que no cumple los requisitos no se finaliza ni publica nada")
    void completeProfile_shouldPublishNothing_whenRequirementsAreMissing() {
        var uid = newUid();
        ReplicatedBirthDates.insertAdult(jdbcTemplate, uid);
        var id = creation.createProfile(new CreateProfileCommand(uid)).getId().value();

        assertThatThrownBy(() -> profiles.completeProfile(id, uid)).isInstanceOf(IncompleteProfileException.class);

        assertThat(outboxRows(id)).isZero();
        assertThat(nothing()).isNull();
    }

    @Test
    @DisplayName("Finalizar veinte veces un perfil publica un solo evento")
    void completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes() {
        var uid = newUid();
        var id = draftProfile(uid);
        profiles.completeProfile(id, uid);

        for (int i = 0; i < 19; i++) {
            assertThatThrownBy(() -> profiles.completeProfile(id, uid)).isInstanceOf(ProfileAlreadyCompletedException.class);
        }

        assertThat(outboxRows(id)).isEqualTo(1);
        assertThat(next()).isNotNull();
        assertThat(nothing()).isNull();
    }

    @Test
    @DisplayName("Si la base falla al finalizar no queda fila, ni mensaje, y el perfil sigue en borrador")
    void completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion() {
        var uid = newUid();
        var id = draftProfile(uid);
        var suffix = UUID.randomUUID().toString().replace("-", "");
        triggers.add(suffix);
        jdbcTemplate.execute("CREATE FUNCTION fail_completion_" + suffix + "() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'forced failure'; END $$");
        jdbcTemplate.execute("CREATE TRIGGER fail_completion_" + suffix + " BEFORE UPDATE OF estado ON perfil_profesional "
                + "FOR EACH ROW WHEN (NEW.firebase_uid = '" + uid + "') EXECUTE FUNCTION fail_completion_" + suffix + "()");

        assertThatThrownBy(() -> profiles.completeProfile(id, uid)).isInstanceOf(RuntimeException.class);

        assertThat(outboxRows(id)).isZero();
        assertThat(nothing()).isNull();
        assertThat(status(id)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Agregar una habilidad a un perfil activo publica un evento con las dos habilidades")
    void addSkill_shouldPublishOneEvent_whenProfileIsCompleted() {
        var uid = newUid();
        var id = draftProfile(uid);
        profiles.completeProfile(id, uid);
        var firstUpdate = body(next()).get("actualizadoEn").asString();

        profiles.addSkill(new AddSkillCommand(id, uid, "SQL", "BASIC"));

        var json = body(next());
        assertThat(json.get("contextoProfesional").get("habilidades")).hasSize(2);
        assertThat(json.get("actualizadoEn").asString()).isGreaterThanOrEqualTo(firstUpdate);
        assertThat(nothing()).isNull();
    }

    @Test
    @DisplayName("Un perfil en borrador no publica nada al agregar una habilidad")
    void addSkill_shouldPublishNothing_whenProfileIsDraft() {
        var uid = newUid();
        var id = draftProfile(uid);

        profiles.addSkill(new AddSkillCommand(id, uid, "SQL", "BASIC"));

        assertThat(outboxRows(id)).isZero();
        assertThat(nothing()).isNull();
    }

    @Test
    @DisplayName("Otro usuario no puede cambiar el perfil activo y no se publica nada")
    void addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser() {
        var owner = newUid();
        var id = activeProfile(owner);
        var intruder = newUid();
        var rowsBefore = outboxRows(id);

        assertThatThrownBy(() -> profiles.addSkill(new AddSkillCommand(id, intruder, "SQL", "BASIC")))
                .isInstanceOf(ProfileAccessDeniedException.class);

        assertThat(outboxRows(id)).isEqualTo(rowsBefore);
        assertThat(nothing()).isNull();
    }

    @Test
    @DisplayName("Enviar dos veces el mismo cambio publica dos eventos con el mismo contenido y el instante sin retroceder")
    void updateProfileInfo_shouldPublishTwoEvents_whenSameChangeIsSentTwice() {
        var uid = newUid();
        var id = activeProfile(uid);
        var command = new UpdateProfileInfoCommand(id, uid, "Analista senior", null, null);

        profiles.updateProfileInfo(command);
        profiles.updateProfileInfo(command);

        var first = body(next());
        var second = body(next());
        assertThat(outboxRows(id)).as("el de activar más los dos cambios").isEqualTo(3);
        assertThat(nothing()).isNull();
        assertThat(second.get("contextoProfesional")).isEqualTo(first.get("contextoProfesional"));
        assertThat(second.get("actualizadoEn").asString()).isGreaterThanOrEqualTo(first.get("actualizadoEn").asString());
    }

    @Test
    @DisplayName("Quitar una habilidad de un perfil activo publica el perfil ya sin ella")
    void removeSkill_shouldPublishProfileWithoutIt_whenProfileIsCompleted() {
        var uid = newUid();
        var id = draftProfile(uid);
        profiles.addSkill(new AddSkillCommand(id, uid, "SQL", "BASIC"));
        profiles.completeProfile(id, uid);
        assertThat(body(next()).get("contextoProfesional").get("habilidades")).hasSize(2);
        var sqlId = jdbcTemplate.queryForObject("select id from habilidad_perfil where perfil_id = ? and nombre_habilidad = 'SQL'",
                UUID.class, id);

        profiles.removeSkill(id, uid, sqlId);

        var skills = body(next()).get("contextoProfesional").get("habilidades");
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).get("nombre").asString()).isEqualTo("Python");
    }

    @Test
    @DisplayName("Si la base rechaza guardar el evento, el cambio se revierte y no se publica nada")
    void addSkill_shouldRollBackChange_whenEventRowCannotBeStored() {
        var uid = newUid();
        var id = activeProfile(uid);
        var suffix = UUID.randomUUID().toString().replace("-", "");
        eventTriggers.add(suffix);
        jdbcTemplate.execute("CREATE FUNCTION fail_event_" + suffix + "() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'forced failure'; END $$");
        jdbcTemplate.execute("CREATE TRIGGER fail_event_" + suffix + " BEFORE INSERT ON evento_saliente "
                + "FOR EACH ROW EXECUTE FUNCTION fail_event_" + suffix + "()");
        var rowsBefore = outboxRows(id);

        assertThatThrownBy(() -> profiles.addSkill(new AddSkillCommand(id, uid, "SQL", "BASIC"))).isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from habilidad_perfil where perfil_id = ?", Integer.class, id))
                .as("la habilidad no quedó guardada").isEqualTo(1);
        assertThat(outboxRows(id)).isEqualTo(rowsBefore);
        assertThat(nothing()).isNull();
    }

    @RepeatedTest(10)
    @DisplayName("Finalizar y quitar la única habilidad a la vez publica un evento con su habilidad o ninguno")
    void completeAndRemoveSkill_shouldPublishOneEventWithSkills_whenBothRunAtOnce() throws Exception {
        var uid = newUid();
        var id = draftProfile(uid);
        var skillId = jdbcTemplate.queryForObject("select id from habilidad_perfil where perfil_id = ?", UUID.class, id);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> completion = executor.submit(raced(start, () -> profiles.completeProfile(id, uid)));
            Future<Boolean> removal = executor.submit(raced(start, () -> profiles.removeSkill(id, uid, skillId)));
            start.countDown();

            boolean completed = completion.get(30, TimeUnit.SECONDS);
            boolean removed = removal.get(30, TimeUnit.SECONDS);

            assertThat(completed ^ removed).as("exactamente una de las dos escrituras se aplica").isTrue();
            if (completed) {
                assertThat(outboxRows(id)).isEqualTo(1);
                assertThat(body(next()).get("contextoProfesional").get("habilidades")).hasSize(1);
            } else {
                assertThat(outboxRows(id)).isZero();
                assertThat(nothing()).isNull();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** Ejecuta la escritura al soltar la salida; solo el rechazo de negocio esperado cuenta como «no se aplicó». */
    private static Callable<Boolean> raced(CountDownLatch start, Runnable write) {
        return () -> {
            start.await();
            try {
                write.run();
                return true;
            } catch (IncompleteProfileException | SkillNotAllowedException expected) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("Si el broker rechaza el mensaje la finalización responde bien, la fila queda pendiente y el relevo la publica después")
    void completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater() {
        var uid = newUid();
        var id = draftProfile(uid);
        rabbitAdmin.deleteExchange(RabbitConfig.EXCHANGE_PERFIL);

        profiles.completeProfile(id, uid);

        awaitOutboxRow(id, "intentos = 1");
        var row = jdbcTemplate.queryForMap("select id, intentos, fecha_publicacion from evento_saliente where agregado_id = ?", id);
        assertThat(((Number) row.get("intentos")).intValue()).isEqualTo(1);
        assertThat(row.get("fecha_publicacion")).isNull();
        assertThat(status(id)).isEqualTo("COMPLETED");

        rabbitAdmin.initialize();
        var summary = relay.relayPending();

        assertThat(summary.published()).isEqualTo(1);
        var message = next();
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.get("id").toString());
    }
}
