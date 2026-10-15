package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.support.ActiveProfiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La tabla de salida contra PostgreSQL real: guarda pendientes, los lee del más antiguo al más reciente saltando los que
 * otra transacción bloqueó, borra la carga al publicar, cuenta intentos y rechaza cada fila que rompe una restricción.
 */
@SpringBootTest
@Testcontainers
class OutboxRepositoryAdapterIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String TYPE = "perfil.profesional-actualizado";
    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    @Autowired
    OutboxRepository outbox;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM evento_saliente");
    }

    private OutboundEvent event(Instant createdAt) {
        return new OutboundEvent(UUID.randomUUID(), TYPE, 1, UUID.randomUUID(), "{\"usuarioId\":\"u1\"}", "req-1", createdAt);
    }

    private void append(OutboundEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> outbox.append(event));
    }

    @Test
    @DisplayName("Agregar guarda una fila pendiente con su carga, sin intentos y sin fecha de publicación")
    void append_shouldStorePendingRow_whenCalled() {
        var event = event(T0);

        append(event);

        var row = jdbc.queryForMap("SELECT tipo, version, agregado_id, carga::text AS carga, id_correlacion, fecha_publicacion, intentos "
                + "FROM evento_saliente WHERE id = ?", event.id());
        assertThat(row.get("tipo")).isEqualTo(TYPE);
        assertThat(row.get("agregado_id")).isEqualTo(event.aggregateId());
        assertThat((String) row.get("carga")).contains("\"usuarioId\"");
        assertThat(row.get("id_correlacion")).isEqualTo("req-1");
        assertThat(row.get("fecha_publicacion")).isNull();
        assertThat(((Number) row.get("intentos")).intValue()).isZero();
    }

    private ProfileUpdated profileUpdated() {
        return new ProfileUpdated(UUID.randomUUID(), ActiveProfiles.example(), Instant.parse("2026-10-15T12:00:00.123Z"),
                new CorrelationId("req-77"));
    }

    @Test
    @DisplayName("El evento del perfil se guarda pendiente con la carga del contrato v1 y sin embedding")
    void appendProfileUpdated_shouldStorePendingRowWithContractPayload_whenCalledInTransaction() {
        var event = profileUpdated();

        var stored = new TransactionTemplate(transactionManager).execute(status -> outbox.appendProfileUpdated(event));

        var row = jdbc.queryForMap("SELECT tipo, version, agregado_id, id_correlacion, fecha_publicacion, intentos, "
                + "carga->>'usuarioId' AS usuario, carga->'contextoProfesional'->>'estado' AS estado, "
                + "jsonb_exists(carga, 'embedding') AS tiene_embedding FROM evento_saliente WHERE id = ?", event.eventId());
        assertThat(stored.id()).isEqualTo(event.eventId());
        assertThat(row.get("tipo")).isEqualTo("perfil.profesional-actualizado");
        assertThat(((Number) row.get("version")).intValue()).isEqualTo(1);
        assertThat(row.get("agregado_id")).isEqualTo(ActiveProfiles.PROFILE_ID);
        assertThat(row.get("id_correlacion")).isEqualTo("req-77");
        assertThat(row.get("fecha_publicacion")).isNull();
        assertThat(((Number) row.get("intentos")).intValue()).isZero();
        assertThat(row.get("usuario")).isEqualTo(ActiveProfiles.OWNER);
        assertThat(row.get("estado")).isEqualTo("COMPLETED");
        assertThat(row.get("tiene_embedding")).isEqualTo(false);
    }

    @Test
    @DisplayName("Guardar el evento del perfil sin una transacción abierta falla y no deja filas")
    void appendProfileUpdated_shouldFail_whenNoTransactionIsActive() {
        assertThatThrownBy(() -> outbox.appendProfileUpdated(profileUpdated())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_saliente", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Agregar sin una transacción abierta falla: el evento debe viajar con el cambio")
    void append_shouldFail_whenNoTransactionIsOpen() {
        assertThatThrownBy(() -> outbox.append(event(T0))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_saliente", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Si la transacción del cambio se revierte, el evento no queda guardado")
    void append_shouldNotPersist_whenSurroundingTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outbox.append(event(T0));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_saliente", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Leer con bloqueo devuelve primero el pendiente más antiguo y respeta el límite")
    void lockPending_shouldReturnOldestFirst_whenSeveralArePending() {
        var newest = event(T0.plusSeconds(20));
        var oldest = event(T0);
        var middle = event(T0.plusSeconds(10));
        List.of(newest, oldest, middle).forEach(this::append);

        var firstTwo = new TransactionTemplate(transactionManager).execute(status -> outbox.lockPending(2));

        assertThat(firstTwo).extracting(OutboundEvent::id).containsExactly(oldest.id(), middle.id());
    }

    @Test
    @DisplayName("Leer con bloqueo salta las filas que otra transacción ya bloqueó")
    void lockPending_shouldSkipRowsLockedByAnotherTransaction() {
        var first = event(T0);
        var second = event(T0.plusSeconds(1));
        append(first);
        append(second);

        var seenByOther = new TransactionTemplate(transactionManager).execute(outer -> {
            var mine = outbox.lockPending(1);
            assertThat(mine).extracting(OutboundEvent::id).containsExactly(first.id());
            // Otra transacción independiente mientras la primera retiene su bloqueo
            return readFromAnotherTransaction();
        });

        assertThat(seenByOther).extracting(OutboundEvent::id).containsExactly(second.id());
    }

    /** Lee desde un hilo y una transacción distintos, para que el bloqueo de la primera sea visible. */
    private List<OutboundEvent> readFromAnotherTransaction() {
        var result = new java.util.concurrent.atomic.AtomicReference<List<OutboundEvent>>();
        var thread = new Thread(() -> result.set(new TransactionTemplate(transactionManager).execute(status -> outbox.lockPending(1))));
        thread.start();
        try {
            thread.join(10_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    @Test
    @DisplayName("Marcar como publicado fija la fecha, borra la carga y deja de listarlo como pendiente")
    void markPublished_shouldClearPayload_whenConfirmed() {
        var event = event(T0);
        append(event);

        outbox.markPublished(event.id(), T0.plusSeconds(5));

        var row = jdbc.queryForMap("SELECT carga, fecha_publicacion FROM evento_saliente WHERE id = ?", event.id());
        assertThat(row.get("carga")).isNull();
        assertThat(row.get("fecha_publicacion")).isNotNull();
        assertThat(outbox.countPending()).isZero();
    }

    @Test
    @DisplayName("Sumar un intento devuelve el total y dos intentos suman dos")
    void addAttempt_shouldIncrement_whenCalledTwice() {
        var event = event(T0);
        append(event);

        assertThat(outbox.addAttempt(event.id())).isEqualTo(1);
        assertThat(outbox.addAttempt(event.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("Sumar un intento a un evento ya publicado o inexistente devuelve 0 y no cambia nada")
    void addAttempt_shouldReturnZero_whenEventIsNotPending() {
        var event = event(T0);
        append(event);
        outbox.markPublished(event.id(), T0.plusSeconds(1));

        assertThat(outbox.addAttempt(event.id())).isZero();
        assertThat(outbox.addAttempt(UUID.randomUUID())).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenRows")
    @DisplayName("Cada fila que rompe una restricción se rechaza con su nombre")
    void constraint_shouldReject_whenRowBreaksIt(String name, String sql, String constraint) {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(sql, id, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    static Stream<Arguments> brokenRows() {
        var head = "INSERT INTO evento_saliente (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion, fecha_publicacion, intentos) VALUES (?, ";
        return Stream.of(
                Arguments.of("tipo desconocido", head + "'otro.tipo', 1, ?, '{}'::jsonb, 'c1', now(), NULL, 0)", "ck_evento_saliente_tipo"),
                Arguments.of("versión 0", head + "'perfil.profesional-actualizado', 0, ?, '{}'::jsonb, 'c1', now(), NULL, 0)", "ck_evento_saliente_version"),
                Arguments.of("correlación con espacio", head + "'perfil.profesional-actualizado', 1, ?, '{}'::jsonb, 'c 1', now(), NULL, 0)",
                        "ck_evento_saliente_id_correlacion"),
                Arguments.of("intentos negativos", head + "'perfil.profesional-actualizado', 1, ?, '{}'::jsonb, 'c1', now(), NULL, -1)",
                        "ck_evento_saliente_intentos"),
                Arguments.of("publicada con carga", head + "'perfil.profesional-actualizado', 1, ?, '{}'::jsonb, 'c1', now(), now(), 0)",
                        "ck_evento_saliente_carga_pendiente"),
                Arguments.of("pendiente sin carga", head + "'perfil.profesional-actualizado', 1, ?, NULL, 'c1', now(), NULL, 0)",
                        "ck_evento_saliente_carga_pendiente"),
                Arguments.of("publicada antes de crearse", head + "'perfil.profesional-actualizado', 1, ?, NULL, 'c1', now(), now() - interval '1 day', 0)",
                        "ck_evento_saliente_fecha_publicacion"));
    }
}
