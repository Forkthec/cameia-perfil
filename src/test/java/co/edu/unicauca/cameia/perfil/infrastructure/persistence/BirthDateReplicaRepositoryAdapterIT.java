package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.port.BirthDateReplica;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** El adaptador de la réplica contra PostgreSQL real: inserta una vez, conserva la primera fecha y borra por identidad. */
@SpringBootTest
@Testcontainers
class BirthDateReplicaRepositoryAdapterIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final FirebaseUid USER = new FirebaseUid("6f1d2c3b4a5e4f60718293a4b5c6d7e8");
    private static final LocalDate BIRTH_DATE = LocalDate.of(2008, 3, 15);

    @Autowired
    BirthDateReplica replica;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM fecha_nacimiento_usuario");
    }

    @Test
    @DisplayName("Guardar una fecha nueva inserta la fila y la fecha se puede leer")
    void saveIfAbsent_shouldInsert_whenAbsent() {
        boolean inserted = replica.saveIfAbsent(USER, BIRTH_DATE);

        assertThat(inserted).isTrue();
        assertThat(replica.findBirthDate(USER)).contains(BIRTH_DATE);
    }

    @Test
    @DisplayName("Guardar otra fecha cuando ya hay una devuelve falso y conserva la primera")
    void saveIfAbsent_shouldReturnFalse_whenPresent() {
        replica.saveIfAbsent(USER, BIRTH_DATE);

        boolean inserted = replica.saveIfAbsent(USER, LocalDate.of(2000, 1, 1));

        assertThat(inserted).isFalse();
        assertThat(replica.findBirthDate(USER)).contains(BIRTH_DATE);
    }

    @Test
    @DisplayName("Dos hilos con el mismo Usuario: solo uno inserta")
    void saveIfAbsent_shouldInsertOnce_whenCalledConcurrently() throws Exception {
        var latch = new CountDownLatch(1);
        var transactions = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    latch.await();
                    return transactions.execute(status -> replica.saveIfAbsent(USER, BIRTH_DATE));
                }));
            }
            latch.countDown();

            long inserted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    inserted++;
                }
            }

            assertThat(inserted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fecha_nacimiento_usuario", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Leer una fecha guardada la devuelve")
    void findBirthDate_shouldReturnStoredDate_whenPresent() {
        jdbc.update("INSERT INTO fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento) VALUES (?, ?::date)",
                USER.value(), "2008-02-29");

        assertThat(replica.findBirthDate(USER)).contains(LocalDate.of(2008, 2, 29));
    }

    @Test
    @DisplayName("Leer la fecha de un Usuario sin fila devuelve vacío")
    void findBirthDate_shouldBeEmpty_whenAbsent() {
        assertThat(replica.findBirthDate(USER)).isEmpty();
    }

    @Test
    @DisplayName("Borrar una fila existente devuelve verdadero y la quita")
    void delete_shouldReturnTrue_whenPresent() {
        replica.saveIfAbsent(USER, BIRTH_DATE);

        assertThat(replica.delete(USER)).isTrue();
        assertThat(replica.findBirthDate(USER)).isEmpty();
    }

    @Test
    @DisplayName("Borrar una fila inexistente devuelve falso")
    void delete_shouldReturnFalse_whenAbsent() {
        assertThat(replica.delete(USER)).isFalse();
    }
}
