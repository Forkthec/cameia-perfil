package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileUpdateInProgressException;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Escrituras simultáneas sobre un mismo perfil contra PostgreSQL real.
 *
 * <p>Sin {@code @Transactional} en la clase: cada hilo necesita su propia transacción, como cada
 * petición HTTP. Los perfiles de prueba usan identidades {@code uid-lock-} y se borran al terminar.</p>
 */
@SpringBootTest
@Testcontainers
class ProfileWriteLockIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    ProfileAppService profileAppService;

    @Autowired
    ProfileCreationAppService profileCreationAppService;

    @Autowired
    ProfessionalProfileRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private String uid;
    private UUID profileId;

    @BeforeEach
    void createProfile() {
        uid = "uid-lock-" + UUID.randomUUID();
        profileId = profileCreationAppService.createProfile(new CreateProfileCommand(uid)).getId().value();
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbcTemplate.update("delete from perfil_profesional where firebase_uid like 'uid-lock-%'");
    }

    /** Abre una transacción que bloquea el perfil y lo retiene hasta soltar el cierre o agotar la espera dada. */
    private Future<?> holdLock(CountDownLatch locked, CountDownLatch release, long maxSeconds) {
        return executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            repository.findByIdForUpdate(ProfileId.of(profileId));
            locked.countDown();
            try {
                release.await(maxSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            status.setRollbackOnly();
            return null;
        }));
    }

    private Future<?> renameInBackground(String name) {
        return executor.submit(() -> profileAppService.updateProfileInfo(
                new UpdateProfileInfoCommand(profileId, uid, name, null, null, null)));
    }

    private String storedName() {
        return jdbcTemplate.queryForObject("select nombre from perfil_profesional where id = ?", String.class, profileId);
    }

    @Test
    @DisplayName("Una escritura espera a que termine la otra que tiene el perfil y luego se aplica")
    void write_shouldWaitForOtherWrite_whenSameProfileIsLocked() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = holdLock(locked, release, TIMEOUT_SECONDS);
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var waiting = renameInBackground("Nuevo");

        // Mientras A retiene la fila, B sigue esperando: el bloqueo se está tomando.
        assertThatThrownBy(() -> waiting.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();

        assertThat(waiting).succeedsWithin(10, TimeUnit.SECONDS);
        assertThat(holder).succeedsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(storedName()).isEqualTo("Nuevo");
    }

    @Test
    @DisplayName("Si el perfil sigue bloqueado más de 2 s, la escritura se corta con 409 y no cambia nada")
    void write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = holdLock(locked, release, 4);
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var started = System.nanoTime();
        var waiting = renameInBackground("Nuevo");

        assertThatThrownBy(() -> waiting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ProfileUpdateInProgressException.class);
        var elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(elapsedMillis).isBetween(1_500L, 3_500L);
        release.countDown();
        assertThat(holder).succeedsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(storedName()).isNull();
    }

    @Test
    @DisplayName("Leer el perfil no espera al bloqueo de una escritura")
    void read_shouldNotWait_whenProfileIsLocked() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = holdLock(locked, release, TIMEOUT_SECONDS);
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var started = System.nanoTime();
        var profile = profileAppService.getProfile(profileId, uid);

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1_000L);
        assertThat(profile.getId().value()).isEqualTo(profileId);
        release.countDown();
        assertThat(holder).succeedsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Tomar el bloqueo del perfil sin una transacción abierta falla, en lugar de soltarlo en silencio")
    void findByIdForUpdate_shouldFail_whenNoTransactionIsOpen() {
        assertThatThrownBy(() -> repository.findByIdForUpdate(ProfileId.of(profileId)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Con el bloqueo tomado, el resto de la transacción vuelve al límite de espera normal")
    void findByIdForUpdate_shouldRestoreLockTimeout_whenLockIsTaken() {
        var lockTimeout = new TransactionTemplate(transactionManager).execute(status -> {
            repository.findByIdForUpdate(ProfileId.of(profileId));
            return jdbcTemplate.queryForObject("show lock_timeout", String.class);
        });

        assertThat(lockTimeout).isEqualTo("0");
    }
}
