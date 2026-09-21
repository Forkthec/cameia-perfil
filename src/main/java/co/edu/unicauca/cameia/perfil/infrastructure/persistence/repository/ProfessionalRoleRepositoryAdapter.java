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
        return jpa.findById(id).map(e -> toDomain(e, "es"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalRole> findAll() {
        return findAllByLang("es");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalRole> findAllByLang(String lang) {
        if ("en".equals(lang)) {
            return jpa.findAllOrderedEn().stream().map(e -> toDomain(e, "en")).toList();
        }
        return jpa.findAllOrderedEs().stream().map(e -> toDomain(e, "es")).toList();
    }

    private ProfessionalRole toDomain(ProfessionalRoleEntity e, String lang) {
        String nombre = "en".equals(lang) ? e.getNombreEn() : e.getNombre();
        String categoria = "en".equals(lang) ? e.getCategoriaEn() : e.getCategoria();
        return new ProfessionalRole(e.getId(), nombre, categoria);
    }
}
