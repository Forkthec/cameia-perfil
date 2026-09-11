package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;

import java.util.UUID;

/** Representación HTTP de un rol del catálogo (CM-23). */
public record ProfessionalRoleResponse(UUID id, String nombre, String categoria) {
    public static ProfessionalRoleResponse from(ProfessionalRole r) {
        return new ProfessionalRoleResponse(r.id(), r.nombre(), r.categoria());
    }
}
