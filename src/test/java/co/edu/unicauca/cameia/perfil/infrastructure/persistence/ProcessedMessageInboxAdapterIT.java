package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.port.ProcessedMessageInbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** El adaptador del Inbox contra PostgreSQL real: anota una vez, ignora la reentrega y resiste la concurrencia. */
@SpringBootTest
@Testcontainers
class ProcessedMessageInboxAdapterIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final UUID MESSAGE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant PROCESSED_AT = Instant.parse("2026-10-09T15:04:05Z");

    @Autowired
    ProcessedMessageInbox inbox;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM evento_procesado");
    }

    @Test
    @DisplayName("Un mensaje nuevo se anota con su tipo y su instante")
    void registerIfAbsent_shouldReturnTrue_whenMessageIsNew() {
        boolean registered = inbox.registerIfAbsent(MESSAGE_ID, "cuenta.creada", PROCESSED_AT);

        assertThat(registered).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_procesado", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tipo FROM evento_procesado", String.class)).isEqualTo("cuenta.creada");
        assertThat(jdbc.queryForObject("SELECT procesado_en FROM evento_procesado", java.sql.Timestamp.class).toInstant())
                .isEqualTo(PROCESSED_AT);
    }

    @Test
    @DisplayName("Un mensaje ya anotado devuelve falso y no cambia la fila")
    void registerIfAbsent_shouldReturnFalse_whenMessageWasRegistered() {
        inbox.registerIfAbsent(MESSAGE_ID, "cuenta.creada", PROCESSED_AT);

        boolean again = inbox.registerIfAbsent(MESSAGE_ID, "cuenta.creada", PROCESSED_AT.plusSeconds(60));

        assertThat(again).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_procesado", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT procesado_en FROM evento_procesado", java.sql.Timestamp.class).toInstant())
                .isEqualTo(PROCESSED_AT);
    }

    @Test
    @DisplayName("Dos hilos con el mismo mensaje: solo uno lo anota")
    void registerIfAbsent_shouldReturnTrueOnce_whenCalledConcurrently() throws Exception {
        var latch = new CountDownLatch(1);
        var transactions = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    latch.await();
                    return transactions.execute(status -> inbox.registerIfAbsent(MESSAGE_ID, "cuenta.creada", PROCESSED_AT));
                }));
            }
            latch.countDown();

            long registered = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    registered++;
                }
            }

            assertThat(registered).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_procesado", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
