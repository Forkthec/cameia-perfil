package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface ProfessionalRoleJpaRepository extends JpaRepository<ProfessionalRoleEntity, UUID> {

    @Query("SELECT r FROM ProfessionalRoleEntity r ORDER BY r.categoria ASC, r.nombre ASC")
    List<ProfessionalRoleEntity> findAllOrderedEs();

    @Query("SELECT r FROM ProfessionalRoleEntity r ORDER BY r.categoriaEn ASC, r.nombreEn ASC")
    List<ProfessionalRoleEntity> findAllOrderedEn();
}
