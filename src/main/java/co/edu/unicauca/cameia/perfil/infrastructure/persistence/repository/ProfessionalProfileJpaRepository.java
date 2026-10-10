package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalProfileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Pieza 2 del patrón de tres piezas (AGENTS §5.3): repositorio Spring Data.
 * Spring genera la implementación en tiempo de ejecución. El adaptador la inyecta.
 * La capa de aplicación NUNCA ve esta interfaz (test ArchUnit {@code aplicacionNoInyectaRepositoriosDeSpringData}).
 */
interface ProfessionalProfileJpaRepository extends JpaRepository<ProfessionalProfileEntity, UUID> {

    long countByFirebaseUid(String firebaseUid);

    Optional<ProfessionalProfileEntity> findFirstByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);

    /**
     * Lee el perfil y bloquea su fila hasta que termine la transacción en curso.
     *
     * @param id identificador del perfil
     * @return el perfil bloqueado, o vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProfessionalProfileEntity p where p.id = :id")
    Optional<ProfessionalProfileEntity> findLockedById(@Param("id") UUID id);
}
