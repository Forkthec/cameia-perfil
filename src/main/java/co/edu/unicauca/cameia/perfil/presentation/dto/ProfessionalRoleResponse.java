package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Representación HTTP de un rol del catálogo (CM-23). */
public record ProfessionalRoleResponse(
        @Schema(description = "Identificador del rol en el catálogo", format = "uuid",
                example = "a0000001-0000-0000-0000-000000000002")
        UUID id,
        @Schema(description = "Nombre del rol en el idioma pedido con lang", example = "Desarrollador Backend")
        String nombre,
        @Schema(description = "Categoría del rol en el idioma pedido con lang", example = "Desarrollo")
        String categoria) {
    public static ProfessionalRoleResponse from(ProfessionalRole r) {
        return new ProfessionalRoleResponse(r.id(), r.nombre(), r.categoria());
    }
}
