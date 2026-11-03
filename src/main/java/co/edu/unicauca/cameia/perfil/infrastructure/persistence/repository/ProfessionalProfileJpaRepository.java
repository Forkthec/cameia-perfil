package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalProfileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * Perfiles de un usuario con sus roles objetivo en una sola sentencia, del primero creado al último.
     * Sin {@code distinct}: Hibernate agrupa por perfil la unión con los roles.
     *
     * @param uid dueño de los perfiles
     * @return los perfiles con sus roles; vacía si no hay
     */
    @Query("select p from ProfessionalProfileEntity p left join fetch p.targetRoles "
            + "where p.firebaseUid = :uid order by p.createdAt asc, p.id asc")
    List<ProfessionalProfileEntity> findWithTargetRolesByFirebaseUid(@Param("uid") String uid);

    /**
     * Igual que {@link #findWithTargetRolesByFirebaseUid(String)} pero solo con los perfiles de un estado.
     * Es una consulta aparte porque Hibernate no infiere el tipo de un parámetro enumerado nulo.
     *
     * @param uid    dueño de los perfiles
     * @param status estado pedido
     * @return los perfiles con sus roles; vacía si no hay
     */
    @Query("select p from ProfessionalProfileEntity p left join fetch p.targetRoles "
            + "where p.firebaseUid = :uid and p.status = :status order by p.createdAt asc, p.id asc")
    List<ProfessionalProfileEntity> findWithTargetRolesByFirebaseUidAndStatus(@Param("uid") String uid,
            @Param("status") ProfileStatus status);
}
