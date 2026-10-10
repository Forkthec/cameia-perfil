package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ProfessionalRoleJpaRepository extends JpaRepository<ProfessionalRoleEntity, UUID> {

    /** Roles del catálogo con el estado dado (el adaptador pasa el de los activos), en español, por categoría y nombre. Los inactivos no se ofrecen. */
    @Query("SELECT r FROM ProfessionalRoleEntity r WHERE r.estado = :estado ORDER BY r.categoria ASC, r.nombre ASC")
    List<ProfessionalRoleEntity> findAllOrderedEs(@Param("estado") String estado);

    /** Roles del catálogo con el estado dado (el adaptador pasa el de los activos), en inglés, por categoría y nombre. Los inactivos no se ofrecen. */
    @Query("SELECT r FROM ProfessionalRoleEntity r WHERE r.estado = :estado ORDER BY r.categoriaEn ASC, r.nombreEn ASC")
    List<ProfessionalRoleEntity> findAllOrderedEn(@Param("estado") String estado);
}
