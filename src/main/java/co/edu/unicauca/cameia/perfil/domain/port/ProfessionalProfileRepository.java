package co.edu.unicauca.cameia.perfil.domain.port;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;

import java.util.Optional;

/**
 * Puerto de salida del dominio hacia la capa de persistencia.
 *
 * <p>La implementación vive en {@code infrastructure.persistence.repository} y usa Spring Data JPA.
 * El dominio solo conoce esta interfaz — no sabe nada de JPA, Hibernate ni PostgreSQL.
 * El test ArchUnit {@code aplicacionNoInyectaRepositoriosDeSpringData} verifica que el app service
 * inyecte este puerto y no el JpaRepository directamente.
 */
public interface ProfessionalProfileRepository {

    /**
     * Persiste el perfil. Crea o actualiza según si el id ya existe (semántica upsert).
     */
    void save(ProfessionalProfile profile);

    /**
     * Busca un perfil por su identificador de agregado.
     */
    Optional<ProfessionalProfile> findById(ProfileId id);

    /**
     * Verifica si el usuario ya tiene al menos un perfil creado.
     * Usada por CM-16 para aplicar el límite de un perfil en plan gratuito.
     * TODO CM-TBD: la lógica de cuota (Gratis=1, Premium=múltiple) queda pendiente
     * hasta que se integre CuotaPlanReplica en Sprint 2.
     */
    boolean existsByFirebaseUid(FirebaseUid firebaseUid);
}
