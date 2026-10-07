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
     * Cuenta los Perfiles Profesionales del Usuario, en cualquier estado.
     *
     * @param firebaseUid dueño
     * @return cantidad de perfiles
     */
    long countByFirebaseUid(FirebaseUid firebaseUid);

    /**
     * Hace esperar a cualquier otra creación de perfil del mismo Usuario hasta que termine la transacción actual.
     *
     * <p>Debe llamarse dentro de una transacción.</p>
     *
     * @param firebaseUid dueño
     */
    void lockCreationFor(FirebaseUid firebaseUid);

    /**
     * Busca el perfil creado más recientemente por el Usuario.
     *
     * @param firebaseUid dueño
     * @return el perfil, o vacío si no tiene
     */
    Optional<ProfessionalProfile> findLatestByFirebaseUid(FirebaseUid firebaseUid);
}
