package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    ProfileAppService profileAppService;

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
    @DisplayName("Cinco peticiones simultáneas del mismo Usuario reciben el mismo perfil y queda uno solo")
    void createProfile_shouldReturnSameProfileToAll_whenFiveRequestsArriveTogether() throws Exception {
        var uid = "uid-it-" + UUID.randomUUID();

        var profiles = runTogether(i -> uid);

        assertThat(profiles).hasSize(REQUESTS);
        assertThat(profiles.stream().map(p -> p.getId().value()).distinct()).hasSize(1);
        assertThat(countProfiles(uid)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cinco Usuarios distintos que crean a la vez no se esperan entre sí y tienen un perfil cada uno")
    void createProfile_shouldNotBlockOtherUsers_whenFiveUsersCreateTogether() throws Exception {
        var prefix = "uid-it-" + UUID.randomUUID() + "-";

        var profiles = runTogether(i -> prefix + i);

        assertThat(profiles.stream().map(p -> p.getId().value()).distinct()).hasSize(REQUESTS);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from perfil_profesional where firebase_uid like ?", Integer.class, prefix + "%"))
                .isEqualTo(REQUESTS);
    }

    @Test
    @DisplayName("Si la creación en proceso se deshace, la petición que esperaba crea el perfil")
    void createProfile_shouldCreate_whenPreviousCreationRolledBack() throws Exception {
        var uid = "uid-it-" + UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var failed = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            repository.lockCreationFor(new FirebaseUid(uid));
            locked.countDown();
            // Da tiempo a que la otra petición llegue al bloqueo y quede esperando.
            sleep(300);
            throw new IllegalStateException("creación deshecha a propósito");
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        var waiting = executor.submit(() -> profileAppService.createProfile(new CreateProfileCommand(uid)));

        assertThat(waiting.get(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(failed).failsWithin(10, TimeUnit.SECONDS);
        assertThat(countProfiles(uid)).isEqualTo(1);
    }

    /** Lanza una creación por hilo y las suelta a la vez con un mismo pistoletazo de salida. */
    private List<ProfessionalProfile> runTogether(IntFunction<String> uidOf) throws Exception {
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<ProfessionalProfile>>();
        for (int i = 0; i < REQUESTS; i++) {
            var uid = uidOf.apply(i);
            Callable<ProfessionalProfile> create = () -> {
                start.await();
                return profileAppService.createProfile(new CreateProfileCommand(uid));
            };
            futures.add(executor.submit(create));
        }
        start.countDown();
        var profiles = new ArrayList<ProfessionalProfile>();
        for (var future : futures) {
            profiles.add(future.get(30, TimeUnit.SECONDS));
        }
        return profiles;
    }

    private int countProfiles(String uid) {
        return jdbcTemplate.queryForObject(
                "select count(*) from perfil_profesional where firebase_uid = ?", Integer.class, uid);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
