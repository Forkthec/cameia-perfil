package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.support.ReplicatedBirthDates;
import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationNotAllowedException;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileUpdateInProgressException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
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
        ReplicatedBirthDates.insertAdult(jdbcTemplate, uid);
        profileId = profileCreationAppService.createProfile(new CreateProfileCommand(uid)).getId().value();
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbcTemplate.update("delete from perfil_profesional where firebase_uid like 'uid-lock-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbcTemplate, "uid-lock-");
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
                new UpdateProfileInfoCommand(profileId, uid, name, null, null)));
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

    @Test
    @DisplayName("Dos experiencias simultáneas sobre un perfil con tres dejan cuatro: la segunda recibe el máximo")
    void addWorkExperience_shouldKeepFour_whenTwoRequestsArriveWithThree() throws Exception {
        for (int i = 0; i < 3; i++) {
            profileAppService.addWorkExperience(command("Empresa " + i));
        }
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            profileAppService.addWorkExperience(command("Empresa A"));
            locked.countDown();
            try {
                release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return null;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var second = executor.submit(() -> profileAppService.addWorkExperience(command("Empresa B")));
        assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();

        assertThat(first).succeedsWithin(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WorkExperienceLimitReachedException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from experiencia_laboral where perfil_id = ?", Integer.class,
                profileId)).isEqualTo(4);
    }

    @Test
    @DisplayName("Dos pestañas que eliminan las dos formaciones de un perfil activo: la segunda recibe que no puede quedarse sin formación")
    void removeEducation_shouldRejectSecond_whenTwoTabsDeleteBothEducationsOfActiveProfile() throws Exception {
        var completed = completeProfileWithEducations(2);
        var first = completed.getEducations().get(0).getId();
        var second = completed.getEducations().get(1).getId();

        var held = removeEducationHoldingTransaction(first);

        var other = executor.submit(() -> profileAppService.removeEducation(profileId, uid, second));
        assertThatThrownBy(() -> other.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        held.release().countDown();

        assertThat(held.result()).succeedsWithin(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> other.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EducationNotAllowedException.class);
        assertThat(jdbcTemplate.queryForList("select id from educacion where perfil_id = ?", UUID.class, profileId))
                .containsExactly(second);
        assertThat(jdbcTemplate.queryForObject("select estado from perfil_profesional where id = ?", String.class, profileId))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Dos bajas simultáneas de la misma formación: una termina bien y la otra recibe que no existe")
    void removeEducation_shouldReturnNotFound_whenTwoTabsDeleteSameEducation() throws Exception {
        var profile = completeProfileWithEducations(3);
        var target = profile.getEducations().get(0).getId();

        var held = removeEducationHoldingTransaction(target);

        var other = executor.submit(() -> profileAppService.removeEducation(profileId, uid, target));
        assertThatThrownBy(() -> other.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        held.release().countDown();

        assertThat(held.result()).succeedsWithin(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> other.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EducationNotFoundException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from educacion where perfil_id = ?", Integer.class, profileId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Vaciar el resumen mientras otra pestaña finaliza: la finalización espera, ve el resumen vacío y se rechaza")
    void updateProfileInfo_shouldNeverLeaveActiveProfileWithoutSummary_whenCompletionRunsAtTheSameTime() throws Exception {
        prepareCompletableProfile(1);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var clearing = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            profileAppService.updateProfileInfo(new UpdateProfileInfoCommand(profileId, uid, null, "", null));
            locked.countDown();
            awaitRelease(release);
            return null;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var completion = executor.submit(() -> profileAppService.completeProfile(profileId, uid));
        assertThatThrownBy(() -> completion.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();

        assertThat(clearing).succeedsWithin(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> completion.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IncompleteProfileException.class);
        assertThat(jdbcTemplate.queryForObject("select estado from perfil_profesional where id = ?", String.class, profileId))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject("select resumen from perfil_profesional where id = ?", String.class, profileId))
                .isNull();
    }

    @Test
    @DisplayName("Finalizar mientras otra pestaña vacía el resumen: el vaciado espera, ve el perfil activo y se rechaza")
    void updateProfileInfo_shouldRejectClear_whenCompletionHoldsTheLock() throws Exception {
        prepareCompletableProfile(1);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completing = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            profileAppService.completeProfile(profileId, uid);
            locked.countDown();
            awaitRelease(release);
            return null;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        var clearing = executor.submit(
                () -> profileAppService.updateProfileInfo(new UpdateProfileInfoCommand(profileId, uid, null, "", null)));
        assertThatThrownBy(() -> clearing.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();

        assertThat(completing).succeedsWithin(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> clearing.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InvalidFieldsException.class);
        assertThat(jdbcTemplate.queryForObject("select estado from perfil_profesional where id = ?", String.class, profileId))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("select resumen from perfil_profesional where id = ?", String.class, profileId))
                .isEqualTo("Resumen de prueba con más de diez caracteres.");
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Resultado de una baja que mantiene su transacción abierta: la futura baja y el cierre que la deja confirmar. */
    private record HeldRemoval(Future<?> result, CountDownLatch release) { }

    /** Elimina la formación dentro de una transacción que no confirma hasta soltar el cierre, y espera a que tenga el bloqueo. */
    private HeldRemoval removeEducationHoldingTransaction(UUID educationId) throws InterruptedException {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var result = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            profileAppService.removeEducation(profileId, uid, educationId);
            locked.countDown();
            try {
                release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return null;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return new HeldRemoval(result, release);
    }

    /** Deja el perfil activo con lo que pide finalizar y el número dado de formaciones. */
    private ProfessionalProfile completeProfileWithEducations(int educations) {
        prepareCompletableProfile(educations);
        profileAppService.completeProfile(profileId, uid);
        return profileAppService.getProfile(profileId, uid);
    }

    /** Deja el perfil con todo lo que pide finalizar y el número dado de formaciones, sin finalizarlo. */
    private void prepareCompletableProfile(int educations) {
        profileAppService.updateProfileInfo(new UpdateProfileInfoCommand(
                profileId, uid, "Perfil de prueba", "Resumen de prueba con más de diez caracteres.", null));
        for (int i = 0; i < educations; i++) {
            profileAppService.addEducation(new AddEducationCommand(profileId, uid, "Universidad " + i,
                    "Ingeniería de Sistemas", "UNDERGRADUATE", "2018-01", "2022-12", false));
        }
        profileAppService.addSkill(new AddSkillCommand(profileId, uid, "Java", "ADVANCED", "MANUAL"));
        profileAppService.addTargetRole(new AddTargetRoleCommand(profileId, uid,
                UUID.fromString("a0000001-0000-0000-0000-000000000002")));
    }

    private AddWorkExperienceCommand command(String company) {
        return new AddWorkExperienceCommand(profileId, uid, company, "Dev", null, "2024-06", null, "CURRENT");
    }
}
