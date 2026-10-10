package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileCreationInProgressException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Creación de perfiles con peticiones simultáneas contra PostgreSQL real.
 *
 * <p>Sin {@code @Transactional} en la clase: cada hilo necesita su propia transacción, como cada
 * petición HTTP. Los perfiles de prueba usan identidades {@code uid-it-} y se borran al terminar.</p>
 */
@SpringBootTest
@Testcontainers
class ProfileCreationConcurrencyIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final int REQUESTS = 5;
    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    ProfileCreationAppService profileCreationAppService;

    @Autowired
    ProfessionalProfileRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbcTemplate.update("delete from perfil_profesional where firebase_uid like 'uid-it-%'");
    }

    @Test
    @DisplayName("Cinco peticiones simultáneas del mismo Usuario dejan un solo perfil y cada una recibe ese perfil o el cupo")
    void createProfile_shouldLeaveOneProfile_whenFiveRequestsArriveTogether() throws Exception {
        var uid = "uid-it-" + UUID.randomUUID();

        var outcomes = runTogether(i -> uid);

        var profileId = jdbcTemplate.queryForObject(
                "select id from perfil_profesional where firebase_uid = ?", UUID.class, uid);
        // Una petición que llega cuando la creación ya terminó recibe el cupo alcanzado (409), nunca otro perfil.
        assertThat(outcomes).allSatisfy(outcome -> {
            if (outcome instanceof ProfessionalProfile profile) {
                assertThat(profile.getId().value()).isEqualTo(profileId);
            } else {
                assertThat(outcome).isInstanceOf(ProfileLimitReachedException.class);
            }
        });
        assertThat(countProfiles(uid)).isEqualTo(1);
    }

    @Test
    @DisplayName("Las peticiones que llegan mientras otra crea el perfil reciben ese mismo perfil, sin 409")
    void createProfile_shouldReturnSameProfile_whenRequestsArriveWhileCreating() throws Exception {
        var uid = "uid-it-" + UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        // Una creación en proceso: toma el bloqueo del Usuario y lo retiene hasta que las demás esperan.
        var creating = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            repository.lockCreationFor(new FirebaseUid(uid));
            locked.countDown();
            await(release);
            var profile = ProfessionalProfile.create(new FirebaseUid(uid));
            repository.save(profile);
            return profile;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        var waiting = new ArrayList<Future<ProfessionalProfile>>();
        for (int i = 1; i < REQUESTS; i++) {
            waiting.add(executor.submit(() -> profileCreationAppService.createProfile(new CreateProfileCommand(uid))));
        }

        awaitWaitingLocks(REQUESTS - 1);
        release.countDown();

        var createdId = creating.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getId();
        for (var future : waiting) {
            assertThat(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getId()).isEqualTo(createdId);
        }
        assertThat(countProfiles(uid)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una petición que llega cuando el Usuario ya tenía su perfil recibe el cupo alcanzado")
    void createProfile_shouldThrowLimitReached_whenRequestArrivesAfterCreationFinished() {
        var uid = "uid-it-" + UUID.randomUUID();
        profileCreationAppService.createProfile(new CreateProfileCommand(uid));

        assertThatThrownBy(() -> profileCreationAppService.createProfile(new CreateProfileCommand(uid)))
                .isInstanceOf(ProfileLimitReachedException.class);
        assertThat(countProfiles(uid)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cinco Usuarios distintos que crean a la vez no se esperan entre sí y tienen un perfil cada uno")
    void createProfile_shouldNotBlockOtherUsers_whenFiveUsersCreateTogether() throws Exception {
        var prefix = "uid-it-" + UUID.randomUUID() + "-";

        var outcomes = runTogether(i -> prefix + i);

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(ProfessionalProfile.class));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from perfil_profesional where firebase_uid like ?", Integer.class, prefix + "%"))
                .isEqualTo(REQUESTS);
    }

    @Test
    @DisplayName("Si la creación en proceso se deshace, la petición que esperaba crea el perfil")
    void createProfile_shouldCreate_whenPreviousCreationRolledBack() throws Exception {
        var uid = "uid-it-" + UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failed = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            repository.lockCreationFor(new FirebaseUid(uid));
            locked.countDown();
            await(release);
            throw new IllegalStateException("creación deshecha a propósito");
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        var waiting = executor.submit(() -> profileCreationAppService.createProfile(new CreateProfileCommand(uid)));

        awaitWaitingLocks(1);
        release.countDown();

        assertThat(waiting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(failed).failsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(countProfiles(uid)).isEqualTo(1);
    }

    @Test
    @DisplayName("Si la creación en proceso no termina, la que espera se corta a los 2 s con 409 y sin crear nada")
    void createProfile_shouldFailFast_whenLockIsHeldLongerThanTimeout() throws Exception {
        var uid = "uid-it-" + UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            repository.lockCreationFor(new FirebaseUid(uid));
            locked.countDown();
            await(release);
            status.setRollbackOnly();
            return null;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var started = System.nanoTime();
        var waiting = executor.submit(() -> profileCreationAppService.createProfile(new CreateProfileCommand(uid)));

        assertThat(outcomeOf(waiting)).isInstanceOf(ProfileCreationInProgressException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(1_500L, 3_500L);
        release.countDown();
        assertThat(holder).succeedsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(countProfiles(uid)).isZero();
    }

    @Test
    @DisplayName("Tomar el bloqueo de creación sin una transacción abierta falla, en lugar de soltarlo en silencio")
    void lockCreationFor_shouldFail_whenNoTransactionIsOpen() {
        assertThatThrownBy(() -> repository.lockCreationFor(new FirebaseUid("uid-it-" + UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Con el bloqueo tomado, el resto de la transacción vuelve al límite de espera normal")
    void lockCreationFor_shouldRestoreLockTimeout_whenLockIsTaken() {
        var uid = new FirebaseUid("uid-it-" + UUID.randomUUID());

        var lockTimeout = new TransactionTemplate(transactionManager).execute(status -> {
            repository.lockCreationFor(uid);
            return jdbcTemplate.queryForObject("show lock_timeout", String.class);
        });

        assertThat(lockTimeout).isEqualTo("0");
    }

    /**
     * Lanza una creación por hilo y las suelta a la vez. Cada resultado es el perfil devuelto o la
     * excepción de negocio que lanzó la creación.
     */
    private List<Object> runTogether(IntFunction<String> uidOf) throws Exception {
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<ProfessionalProfile>>();
        for (int i = 0; i < REQUESTS; i++) {
            var uid = uidOf.apply(i);
            futures.add(executor.submit(() -> {
                start.await();
                return profileCreationAppService.createProfile(new CreateProfileCommand(uid));
            }));
        }
        start.countDown();
        var outcomes = new ArrayList<Object>();
        for (var future : futures) {
            outcomes.add(outcomeOf(future));
        }
        return outcomes;
    }

    private static Object outcomeOf(Future<ProfessionalProfile> future) throws Exception {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    /** Espera a que la cantidad dada de transacciones esté detenida en un bloqueo por Usuario. */
    private void awaitWaitingLocks(int expected) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (waitingLocks() < expected) {
            assertThat(System.nanoTime()).as("transacciones esperando el bloqueo").isLessThan(deadline);
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }

    private int waitingLocks() {
        return jdbcTemplate.queryForObject(
                "select count(*) from pg_locks where locktype = 'advisory' and not granted", Integer.class);
    }

    private int countProfiles(String uid) {
        return jdbcTemplate.queryForObject(
                "select count(*) from perfil_profesional where firebase_uid = ?", Integer.class, uid);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
