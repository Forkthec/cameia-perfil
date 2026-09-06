package co.edu.unicauca.cameia.perfil.presentation.dto;

/** Body del POST /api/v1/profiles/{id}/target-roles (CM-20). */
public record AddTargetRoleRequest(String title, String seniority, String provenance) {
}
