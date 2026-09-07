package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración de la capa de persistencia.
 *
 * <p>Requiere PostgreSQL real vía docker-compose. La anotación {@code @Transactional}
 * garantiza rollback automático al final de cada test: la BD queda limpia sin fixture de limpieza.
 *
 * <p>No usa H2 ni Testcontainers (AGENTS.md §9). Corre con:
 * {@code docker compose run --rm verify}
 */
@SpringBootTest
@Transactional
class ProfessionalProfileRepositoryAdapterIT {

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
    void existsByFirebaseUid_returnsTrueAfterSave() {
        var uid = new FirebaseUid("firebase-it-exists-002");
        repository.save(ProfessionalProfile.create(uid));

        assertThat(repository.existsByFirebaseUid(uid)).isTrue();
    }

    @Test
    void existsByFirebaseUid_returnsFalseForUnknownUid() {
        var unknown = new FirebaseUid("firebase-it-unknown-9999");

        assertThat(repository.existsByFirebaseUid(unknown)).isFalse();
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
