package co.edu.unicauca.cameia.perfil.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body del PATCH /api/v1/profiles/{id}/target-roles/{roleId} (CM-23). */
public record UpdateTargetRoleRequest(@NotNull UUID professionalRoleId) {}
