package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface ProfessionalRoleJpaRepository extends JpaRepository<ProfessionalRoleEntity, UUID> {

    /** Roles que el catálogo ofrece hoy, en español, por categoría y nombre. Los inactivos no se ofrecen. */
    @Query("SELECT r FROM ProfessionalRoleEntity r WHERE r.estado = 'ACTIVE' ORDER BY r.categoria ASC, r.nombre ASC")
    List<ProfessionalRoleEntity> findAllOrderedEs();

    /** Roles que el catálogo ofrece hoy, en inglés, por categoría y nombre. Los inactivos no se ofrecen. */
    @Query("SELECT r FROM ProfessionalRoleEntity r WHERE r.estado = 'ACTIVE' ORDER BY r.categoriaEn ASC, r.nombreEn ASC")
    List<ProfessionalRoleEntity> findAllOrderedEn();
}
