package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalRoleEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ProfessionalRoleRepositoryAdapter implements ProfessionalRoleRepository {

    private final ProfessionalRoleJpaRepository jpa;

    ProfessionalRoleRepositoryAdapter(ProfessionalRoleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalRole> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalRole> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    private ProfessionalRole toDomain(ProfessionalRoleEntity e) {
        return new ProfessionalRole(e.getId(), e.getNombre(), e.getCategoria());
    }
}
