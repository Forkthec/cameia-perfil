package co.edu.unicauca.cameia.perfil.domain.port;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de dominio para el catálogo de roles profesionales (CM-23). */
public interface ProfessionalRoleRepository {
    Optional<ProfessionalRole> findById(UUID id);
    List<ProfessionalRole> findAllByLang(String lang);

    /**
     * Lee el catálogo completo para anunciarlo: todos los roles, activos e inactivos, con los dos idiomas.
     *
     * @return los roles por nombre en español; el nombre en inglés de respaldo es el español
     */
    List<ProfessionalRoleNames> findAllWithBothLanguages();
}
