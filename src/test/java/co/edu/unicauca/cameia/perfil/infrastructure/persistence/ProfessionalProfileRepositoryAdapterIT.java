package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationDraft;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.model.SalaryExpectation;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.SkillName;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Prueba de integración de la capa de persistencia.
 *
 * <p>Requiere Docker en ejecución: Testcontainers levanta un PostgreSQL real, no H2. La anotación
 * {@code @Transactional} garantiza rollback automático al final de cada test: la BD queda limpia
 * sin fixture de limpieza.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ProfessionalProfileRepositoryAdapterIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Rol del catálogo que siembra la migración de roles profesionales. */
    private static final UUID CATALOG_ROLE_ID = UUID.fromString("a0000001-0000-0000-0000-000000000001");

    @Autowired
    ProfessionalProfileRepository repository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void save_andFindById_roundtripPreservesAllScalarFields() {
        var uid = new FirebaseUid("firebase-it-round-001");
        var profile = ProfessionalProfile.create(uid);

        repository.save(profile);

        var found = repository.findById(profile.getId());
        assertThat(found).isPresent();
        var loaded = found.get();
        assertThat(loaded.getId()).isEqualTo(profile.getId());
        assertThat(loaded.getFirebaseUid().value()).isEqualTo("firebase-it-round-001");
        assertThat(loaded.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
        assertThat(loaded.getReviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(loaded.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        assertThat(loaded.getName()).isNull();
        assertThat(loaded.getSummary()).isNull();
        assertThat(loaded.getTargetRoles()).isEmpty();
        assertThat(loaded.getWorkExperiences()).isEmpty();
        assertThat(loaded.getEducations()).isEmpty();
        assertThat(loaded.getProfileSkills()).isEmpty();
    }

    @Test
    @DisplayName("Los roles objetivo se leen en el orden en que se agregaron, aunque uno se sustituya")
    void findById_shouldReturnTargetRolesInInsertionOrder() {
        var a = UUID.fromString("a0000001-0000-0000-0000-000000000001");
        var b = UUID.fromString("a0000001-0000-0000-0000-000000000002");
        var c = UUID.fromString("a0000001-0000-0000-0000-000000000003");
        var d = UUID.fromString("a0000001-0000-0000-0000-000000000004");
        var profile = ProfessionalProfile.create(new FirebaseUid("firebase-it-order"));
        var second = new TargetRole(UUID.randomUUID(), b, "Rol B", DataProvenance.MANUAL);
        profile.addTargetRole(new TargetRole(UUID.randomUUID(), a, "Rol A", DataProvenance.MANUAL));
        repository.save(profile);
        profile.addTargetRole(second);
        repository.save(profile);
        profile.addTargetRole(new TargetRole(UUID.randomUUID(), c, "Rol C", DataProvenance.MANUAL));
        repository.save(profile);

        profile.updateTargetRole(second.getId(), d, "Rol D");
        repository.save(profile);

        var loaded = repository.findById(profile.getId()).orElseThrow();
        assertThat(loaded.getTargetRoles()).extracting(TargetRole::getProfessionalRoleId).containsExactly(a, d, c);
        assertThat(loaded.getTargetRoles().get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("Dos Roles Objetivo con el mismo rol del catálogo se traducen a la excepción de negocio de duplicado")
    void save_shouldTranslateUniqueViolation_whenRoleRepeats() {
        var targetRoles = List.of(
                new TargetRole(UUID.randomUUID(), CATALOG_ROLE_ID, "Desarrollador", DataProvenance.MANUAL),
                new TargetRole(UUID.randomUUID(), CATALOG_ROLE_ID, "Desarrollador", DataProvenance.MANUAL));
        var profile = ProfessionalProfile.reconstitute(
                ProfileId.generate(), new FirebaseUid("firebase-it-dup-role"), null, null, null, null,
                DataProvenance.MANUAL, ProfileStatus.IN_PROGRESS, ReviewStatus.PENDING_REVIEW,
                Instant.parse("2026-10-10T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z"),
                targetRoles, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> repository.save(profile)).isInstanceOf(DuplicateTargetRoleException.class);
    }

    @Test
    @DisplayName("El conteo incluye todos los perfiles del Usuario y solo los suyos")
    void countByFirebaseUid_shouldCountOnlyOwnProfiles_whenSeveralExist() {
        var uid = new FirebaseUid("firebase-it-count-002");
        repository.save(ProfessionalProfile.create(uid));
        repository.save(ProfessionalProfile.create(uid));
        repository.save(ProfessionalProfile.create(new FirebaseUid("firebase-it-count-otro")));

        assertThat(repository.countByFirebaseUid(uid)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un Usuario sin perfiles cuenta cero y no tiene perfil reciente")
    void countAndLatest_shouldBeEmpty_whenUserHasNoProfiles() {
        var unknown = new FirebaseUid("firebase-it-unknown-9999");

        assertThat(repository.countByFirebaseUid(unknown)).isZero();
        assertThat(repository.findLatestByFirebaseUid(unknown)).isEmpty();
    }

    @Test
    @DisplayName("El perfil más reciente del Usuario se encuentra con su identificador")
    void findLatestByFirebaseUid_shouldReturnProfile_whenUserHasOne() {
        var uid = new FirebaseUid("firebase-it-latest-003");
        var profile = ProfessionalProfile.create(uid);
        repository.save(profile);

        assertThat(repository.findLatestByFirebaseUid(uid)).get()
                .extracting(ProfessionalProfile::getId).isEqualTo(profile.getId());
    }

    @Test
    @DisplayName("Guardar y leer desde la base conserva los datos generales y todos los elementos del perfil")
    void save_andFindById_shouldPreserveEveryItem_whenProfileIsFull() {
        var profile = ProfessionalProfile.create(new FirebaseUid("firebase-it-round-full"));
        profile.updateName(new ProfileName("Ana Sofía"));
        profile.updateSummary(new ProfessionalSummary("Desarrolladora backend"));
        profile.updateSalaryExpectation(new SalaryExpectation(new BigDecimal("4500000.00")));
        profile.updatePreferredModality(WorkModality.REMOTE);
        var current = WorkExperience.rebuild(UUID.randomUUID(), new WorkExperienceDraft("ACME", "Dev", "APIs de pagos",
                YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT), DataProvenance.MANUAL);
        var ended = WorkExperience.rebuild(UUID.randomUUID(), new WorkExperienceDraft("Globex", "Tester", null,
                YearMonth.of(2019, 3), YearMonth.of(2021, 6), EmploymentStatus.ENDED), DataProvenance.AI_SUGGESTED);
        var finished = Education.rebuild(UUID.randomUUID(), new EducationDraft("Unicauca", "Ingeniería",
                EducationLevel.UNDERGRADUATE, YearMonth.of(2015, 2), YearMonth.of(2020, 12), false), DataProvenance.MANUAL);
        var ongoing = Education.rebuild(UUID.randomUUID(), new EducationDraft("Unicauca", "Maestría",
                EducationLevel.POSTGRADUATE, YearMonth.of(2023, 1), null, true), DataProvenance.MANUAL);
        var role = new TargetRole(UUID.randomUUID(), CATALOG_ROLE_ID, "Desarrollador Frontend", DataProvenance.MANUAL);
        var skill = new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL);
        profile.addWorkExperience(current);
        profile.addWorkExperience(ended);
        profile.addEducation(finished);
        profile.addEducation(ongoing);
        profile.addTargetRole(role);
        profile.addSkill(skill);

        repository.save(profile);
        // Vacía el contexto de persistencia para que la lectura venga de la base y no de la memoria.
        entityManager.flush();
        entityManager.clear();
        var loaded = repository.findById(profile.getId()).orElseThrow();

        assertThat(loaded.getName().value()).isEqualTo("Ana Sofía");
        assertThat(loaded.getSummary().value()).isEqualTo("Desarrolladora backend");
        assertThat(loaded.getSalaryExpectation().amount()).isEqualByComparingTo("4500000");
        assertThat(loaded.getPreferredModality()).isEqualTo(WorkModality.REMOTE);
        assertThat(loaded.getWorkExperiences()).extracting(WorkExperience::getCompany, WorkExperience::getStartDate,
                        WorkExperience::getEndDate, WorkExperience::getEmploymentStatus)
                .containsExactlyInAnyOrder(
                        tuple("ACME", YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT),
                        tuple("Globex", YearMonth.of(2019, 3), YearMonth.of(2021, 6), EmploymentStatus.ENDED));
        assertThat(loaded.getEducations()).extracting(Education::getDegree, Education::getEndDate, Education::isInProgress)
                .containsExactlyInAnyOrder(
                        tuple("Ingeniería", YearMonth.of(2020, 12), false),
                        tuple("Maestría", null, true));
        assertThat(loaded.getTargetRoles()).extracting(TargetRole::getProfessionalRoleId, TargetRole::getRoleTitle)
                .containsExactly(tuple(CATALOG_ROLE_ID, "Desarrollador Frontend"));
        assertThat(loaded.getProfileSkills()).extracting(ProfileSkill::getSkillName, ProfileSkill::getLevel)
                .containsExactly(tuple("Java", SkillLevel.ADVANCED));
    }

    @Test
    void findById_returnsEmptyForUnknownId() {
        var unknownId = ProfileId.generate();

        assertThat(repository.findById(unknownId)).isEmpty();
    }

    @Test
    void save_twiceWithSameId_isIdempotent() {
        var uid = new FirebaseUid("firebase-it-idem-003");
        var profile = ProfessionalProfile.create(uid);

        repository.save(profile);
        repository.save(profile); // segunda llamada no debe lanzar excepción

        assertThat(repository.findById(profile.getId())).isPresent();
    }

    @Test
    @DisplayName("Una fila con el resumen en blanco se lee como perfil sin resumen, no como un fallo")
    void findById_shouldReturnProfileWithoutSummary_whenStoredSummaryIsBlank() {
        var profile = ProfessionalProfile.create(new FirebaseUid("firebase-it-blank-004"));
        repository.save(profile);
        entityManager.flush();
        entityManager.createNativeQuery("update perfil_profesional set resumen = '   ' where id = :id")
                .setParameter("id", profile.getId().value())
                .executeUpdate();
        entityManager.clear();

        var loaded = repository.findById(profile.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getSummary()).isNull();
    }
}
