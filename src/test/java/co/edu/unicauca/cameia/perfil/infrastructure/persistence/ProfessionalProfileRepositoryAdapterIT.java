package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    ProfessionalProfileRepository repository;

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
}
