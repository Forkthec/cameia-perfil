package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfessionalRoleJpaRepository extends JpaRepository<ProfessionalRoleEntity, UUID> {}
