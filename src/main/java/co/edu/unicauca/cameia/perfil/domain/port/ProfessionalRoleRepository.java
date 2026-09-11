package co.edu.unicauca.cameia.perfil.domain.port;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de dominio para el catálogo de roles profesionales (CM-23). */
public interface ProfessionalRoleRepository {
    Optional<ProfessionalRole> findById(UUID id);
    List<ProfessionalRole> findAll();
}
