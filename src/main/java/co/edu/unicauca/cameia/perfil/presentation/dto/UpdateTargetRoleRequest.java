package co.edu.unicauca.cameia.perfil.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/profiles/{id}/target-roles/{roleId}}.
 *
 * <p>El servidor ignora cualquier otro campo: sustituir conserva la procedencia y el lugar del rol objetivo.</p>
 *
 * @param professionalRoleId rol del catálogo por el que se sustituye; debe estar activo
 */
public record UpdateTargetRoleRequest(
        @NotNull
        @Schema(description = "Rol del catálogo por el que se sustituye; debe estar activo", format = "uuid",
                example = "a0000001-0000-0000-0000-000000000003", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID professionalRoleId) {
}
