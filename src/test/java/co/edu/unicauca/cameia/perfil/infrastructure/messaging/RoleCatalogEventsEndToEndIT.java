package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import co.edu.unicauca.cameia.perfil.application.service.OutboxRelayAppService;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * El catálogo de roles de punta a punta contra PostgreSQL y RabbitMQ reales: qué se anota al arrancar, qué llega a la cola de
 * Entrevista y qué pasa si el broker no confirma o si ya hay una instantánea pendiente.
 *
 * <p>Esta clase enciende {@code perfil.events.announce-role-catalog-on-startup}, que el resto de las pruebas apaga. «Arrancar»
 * se simula publicando el {@link ApplicationReadyEvent} que Spring Boot publica al terminar de arrancar.</p>
 */
@SpringBootTest(properties = "perfil.events.announce-role-catalog-on-startup=true")
class RoleCatalogEventsEndToEndIT extends MessagingIntegrationSupport {

    private static final String QUEUE = RabbitConfig.INTERVIEW_ROLE_CATALOG_QUEUE;
    private static final String TYPE = "perfil.catalogo-roles-actualizado";
    private static final UUID BACKEND_ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    ConfigurableApplicationContext context;
    @Autowired
    OutboxRelayAppService relay;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    Queue interviewRoleCatalogQueue;

    @BeforeEach
    void cleanState() {
        // El arranque real del contexto de esta clase también anotó el catálogo: se espera a que termine antes de limpiar.
        await().atMost(Duration.ofSeconds(15)).until(() -> pendingRows() == 0);
        jdbcTemplate.update("delete from evento_saliente");
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    @AfterEach
    void restore() {
        // Una prueba puede haber borrado el exchange: se vuelve a declarar para las clases que comparten el broker.
        rabbitAdmin.initialize();
        jdbcTemplate.update("UPDATE rol_profesional SET estado = 'ACTIVE'");
        jdbcTemplate.update("delete from evento_saliente");
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    // ── ayudantes ──────────────────────────────────────────────────────────

    /** Publica el evento que Spring Boot publica cuando la aplicación termina de arrancar. */
    private void startApplication() {
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
    }

    private int pendingRows() {
        return jdbcTemplate.queryForObject("select count(*) from evento_saliente where tipo = ? and fecha_publicacion is null",
                Integer.class, TYPE);
    }

    private int rows() {
        return jdbcTemplate.queryForObject("select count(*) from evento_saliente where tipo = ?", Integer.class, TYPE);
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

    /** Espera a que el intento posterior al commit, que corre en otro hilo, deje anotado su resultado. */
    private void awaitPublishedRows(int expected) {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbcTemplate.queryForObject(
                "select count(*) from evento_saliente where tipo = ? and fecha_publicacion is not null", Integer.class, TYPE) == expected);
    }

    // ── topología ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("La cola de Entrevista para el catálogo queda ligada a perfil.events con los argumentos acordados")
    void topology_shouldRouteToInterviewQueue_whenServiceStarts() {
        assertThat(interviewRoleCatalogQueue.getArguments()).isEqualTo(Map.of(
                "x-dead-letter-exchange", "entrevista.dlx",
                "x-dead-letter-routing-key", "entrevista.catalogo-roles-actualizado"));

        rabbitTemplate.send(RabbitConfig.EXCHANGE_PERFIL, RabbitConfig.ROLE_CATALOG_KEY,
                MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build());

        assertThat(next()).isNotNull();
    }

    // ── qué se publica ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Al arrancar se publica un solo mensaje con los 46 roles y las propiedades del contrato")
    void startup_shouldPublishOneMessageWithEveryRole_whenNoneIsPending() {
        startApplication();

        var message = next();
        assertThat(message).isNotNull();
        awaitPublishedRows(1);
        var row = jdbcTemplate.queryForMap("select id, version, agregado_id, id_correlacion, carga from evento_saliente where tipo = ?", TYPE);
        var properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(row.get("id").toString());
        assertThat(properties.getType()).isEqualTo(TYPE);
        assertThat(properties.getAppId()).isEqualTo("cameia-perfil");
        assertThat(properties.<Integer>getHeader("x-event-version")).isEqualTo(1);
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getCorrelationId()).isEqualTo("perfil-catalogo-" + properties.getMessageId().substring(0, 8));
        assertThat(row.get("agregado_id")).isEqualTo(new UUID(0L, 0L));
        assertThat(row.get("carga")).as("la carga se borra al confirmarse la publicación").isNull();
        var json = body(message);
        assertThat(json.get("actualizadoEn").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");
        assertThat(json.get("roles")).hasSize(46);
        assertThat(nothing()).isNull();
    }

    @Test
    @DisplayName("Los roles del mensaje son los de la tabla del catálogo, con los dos idiomas y el estado")
    void startup_shouldPublishTheCatalogRows_whenRoleIsInactive() {
        jdbcTemplate.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?", BACKEND_ROLE);

        startApplication();

        var roles = body(next()).get("roles");
        var codes = new ArrayList<String>();
        roles.forEach(role -> codes.add(role.get("codigo").asString()));
        var catalogIds = jdbcTemplate.queryForList("select id::text from rol_profesional", String.class);
        assertThat(codes).containsExactlyInAnyOrderElementsOf(catalogIds);
        var backend = findRole(roles, BACKEND_ROLE);
        assertThat(backend.get("nombreEs").asString()).isEqualTo("Desarrollador Backend");
        assertThat(backend.get("nombreEn").asString()).isEqualTo("Backend Developer");
        assertThat(backend.get("estado").asString()).isEqualTo("INACTIVE");
        assertThat(roles.get(0).get("estado").asString()).isIn("ACTIVE", "INACTIVE");
    }

    private static JsonNode findRole(JsonNode roles, UUID id) {
        for (JsonNode role : roles) {
            if (role.get("codigo").asString().equals(id.toString())) {
                return role;
            }
        }
        throw new AssertionError("El rol " + id + " no está en el mensaje");
    }

    // ── cuándo se publica ──────────────────────────────────────────────────

    @Test
    @DisplayName("Un segundo arranque con la fila ya publicada anota y publica una instantánea nueva")
    void startup_shouldAppendNewRow_whenPreviousOneIsPublished() {
        startApplication();
        var first = next();
        awaitPublishedRows(1);

        startApplication();

        var second = next();
        assertThat(second).isNotNull();
        awaitPublishedRows(2);
        assertThat(rows()).isEqualTo(2);
        assertThat(second.getMessageProperties().getMessageId()).isNotEqualTo(first.getMessageProperties().getMessageId());
        assertThat(body(second).get("roles")).hasSize(46);
    }

    @Test
    @DisplayName("Dos arranques seguidos con el broker sin confirmar dejan una sola fila pendiente, y el relevo la publica después")
    void startup_shouldKeepOnePendingRow_whenBrokerRejectsAndApplicationStartsTwice() {
        rabbitAdmin.deleteExchange(RabbitConfig.EXCHANGE_PERFIL);

        startApplication();
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbcTemplate.queryForObject(
                "select intentos from evento_saliente where tipo = ?", Integer.class, TYPE) == 1);
        startApplication();

        assertThat(rows()).as("el segundo arranque no agrega otra instantánea").isEqualTo(1);
        assertThat(pendingRows()).isEqualTo(1);

        rabbitAdmin.initialize();
        var summary = relay.relayPending();

        assertThat(summary.published()).isEqualTo(1);
        var message = next();
        assertThat(message).isNotNull();
        assertThat(body(message).get("roles")).hasSize(46);
        assertThat(nothing()).isNull();
        assertThat(pendingRows()).isZero();
    }

    @Test
    @DisplayName("Si otra instancia está anotando (tiene el candado), este arranque no agrega ninguna instantánea")
    void startup_shouldAppendNothing_whenAnotherInstanceHoldsTheAnnouncementLock() {
        var template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(other -> {
            assertThat(outbox.tryLockAnnouncement(TYPE)).isTrue();

            // Otra instancia = otro hilo y otra conexión; en este hilo el arranque se uniría a la transacción que tiene el candado.
            CompletableFuture.runAsync(this::startApplication).join();

            assertThat(rows()).isZero();
            assertThat(nothing()).isNull();
        });

        startApplication();

        assertThat(next()).as("con el candado suelto, el siguiente arranque sí anota").isNotNull();
        awaitPublishedRows(1);
    }

    @Test
    @DisplayName("Si la base falla al guardar la instantánea, no queda fila ni mensaje y el arranque sigue")
    void startup_shouldPublishNothing_whenEventRowCannotBeStored() {
        var suffix = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE FUNCTION fail_catalog_" + suffix + "() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'forced failure'; END $$");
        jdbcTemplate.execute("CREATE TRIGGER fail_catalog_" + suffix + " BEFORE INSERT ON evento_saliente "
                + "FOR EACH ROW EXECUTE FUNCTION fail_catalog_" + suffix + "()");
        try {
            startApplication();

            assertThat(rows()).isZero();
            assertThat(nothing()).isNull();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_catalog_" + suffix + " ON evento_saliente");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_catalog_" + suffix + "()");
        }
    }
}
