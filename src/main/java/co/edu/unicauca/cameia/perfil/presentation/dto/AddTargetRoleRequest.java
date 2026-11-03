package co.edu.unicauca.cameia.perfil.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/profiles/{id}/target-roles}.
 *
 * <p>El servidor ignora cualquier otro campo: la procedencia de un rol agregado a mano es siempre MANUAL.</p>
 *
 * @param professionalRoleId rol del catálogo; debe estar activo
 */
public record AddTargetRoleRequest(
        @NotNull
        @Schema(description = "Rol del catálogo; debe estar activo", format = "uuid",
                example = "a0000001-0000-0000-0000-000000000002", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID professionalRoleId) {
}
