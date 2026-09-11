package co.edu.unicauca.cameia.perfil.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body del POST /api/v1/profiles/{id}/target-roles (CM-23). */
public record AddTargetRoleRequest(
        @NotNull UUID professionalRoleId,
        @NotNull String provenance) {
}
