package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adaptador que implementa el puerto de persistencia del perfil con Spring Data JPA.
 *
 * <p>El mapeo entre el agregado y sus entidades lo hace {@link ProfessionalProfileMapping}, privado a
 * este paquete.</p>
 */
@Repository
class ProfessionalProfileRepositoryAdapter implements ProfessionalProfileRepository {

    private final ProfessionalProfileJpaRepository jpa;

    @PersistenceContext
    private EntityManager em;

    ProfessionalProfileRepositoryAdapter(ProfessionalProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(ProfessionalProfile profile) {
        jpa.save(ProfessionalProfileMapping.toEntity(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalProfile> findById(ProfileId id) {
        return jpa.findById(id.value()).map(ProfessionalProfileMapping::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByFirebaseUid(FirebaseUid firebaseUid) {
        return jpa.countByFirebaseUid(firebaseUid.value());
    }

    /**
     * Bloqueo de transacción de PostgreSQL por Usuario; se libera solo al confirmar o deshacer.
     * Sin transacción abierta se liberaría de inmediato, así que exige la del servicio que crea el perfil
     * y falla si no la hay.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCreationFor(FirebaseUid firebaseUid) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:uid, 0))")
                .setParameter("uid", firebaseUid.value())
                .getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalProfile> findLatestByFirebaseUid(FirebaseUid firebaseUid) {
        return jpa.findFirstByFirebaseUidOrderByCreatedAtDesc(firebaseUid.value()).map(ProfessionalProfileMapping::toDomain);
    }
}
