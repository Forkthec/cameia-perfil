package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.exception.ProfileCreationInProgressException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileUpdateInProgressException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalProfileEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Adaptador que implementa el puerto de persistencia del perfil con Spring Data JPA.
 *
 * <p>El mapeo entre el agregado y sus entidades lo hace {@link ProfessionalProfileMapping}, privado a
 * este paquete.</p>
 */
@Repository
class ProfessionalProfileRepositoryAdapter implements ProfessionalProfileRepository {

    /** Espera máxima por el bloqueo de creación de un Usuario; es una constante, nunca un dato recibido. */
    static final String CREATION_LOCK_TIMEOUT = "2s";

    /** Espera máxima por otra escritura sobre el mismo perfil; es una constante, nunca un dato recibido. */
    static final String UPDATE_LOCK_TIMEOUT = "2s";

    /** Estado SQL de PostgreSQL cuando se agota {@code lock_timeout}. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    /** Máximo de causas que se recorren, para no girar en una cadena con ciclo. */
    private static final int MAX_CAUSES = 32;

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

    /**
     * Lee el perfil bloqueando su fila. Una escritura atascada no retiene la conexión de las que esperan:
     * a los 2 s PostgreSQL corta la espera (55P03) y la petición responde 409. El límite rige solo mientras
     * se espera el bloqueo.
     *
     * <p>El valor por defecto se restablece solo en el camino feliz y no en un {@code finally}: cuando
     * PostgreSQL corta la espera la transacción queda abortada (25P02) y cualquier sentencia siguiente
     * lanzaría una segunda excepción que taparía el 409.</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ProfessionalProfile> findByIdForUpdate(ProfileId id) {
        em.createNativeQuery("set local lock_timeout = '" + UPDATE_LOCK_TIMEOUT + "'").executeUpdate();
        Optional<ProfessionalProfileEntity> locked;
        try {
            locked = jpa.findLockedById(id.value());
        } catch (PersistenceException | PessimisticLockingFailureException e) {
            throw isLockNotAvailable(e) ? new ProfileUpdateInProgressException() : e;
        }
        em.createNativeQuery("set local lock_timeout to default").executeUpdate();
        return locked.map(ProfessionalProfileMapping::toDomain);
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
        // Una creación atascada no retiene la conexión de las que esperan: a los 2 s PostgreSQL corta
        // la espera (55P03) y la petición responde 409 con su código. Rige solo mientras espera el bloqueo.
        em.createNativeQuery("set local lock_timeout = '" + CREATION_LOCK_TIMEOUT + "'").executeUpdate();
        try {
            em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:uid, 0))")
                    .setParameter("uid", firebaseUid.value())
                    .getSingleResult();
        } catch (PersistenceException e) {
            throw isLockNotAvailable(e) ? new ProfileCreationInProgressException() : e;
        }
        // El límite era para esperar el bloqueo: las demás sentencias de la transacción no lo heredan.
        em.createNativeQuery("set local lock_timeout to default").executeUpdate();
    }

    /** Indica si la causa del fallo es el estado 55P03 de PostgreSQL: se agotó la espera por un bloqueo. */
    static boolean isLockNotAvailable(Throwable failure) {
        for (var seen = 0; failure != null && seen < MAX_CAUSES; failure = failure.getCause(), seen++) {
            if (failure instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalProfile> findLatestByFirebaseUid(FirebaseUid firebaseUid) {
        return jpa.findFirstByFirebaseUidOrderByCreatedAtDesc(firebaseUid.value()).map(ProfessionalProfileMapping::toDomain);
    }
}
