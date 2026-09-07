package co.edu.unicauca.cameia.perfil.presentation.dto;

/** Body del PATCH /api/v1/profiles/{id}/target-roles/{roleId} (HU-2.11). */
public record UpdateTargetRoleRequest(String title, String seniority) {}
