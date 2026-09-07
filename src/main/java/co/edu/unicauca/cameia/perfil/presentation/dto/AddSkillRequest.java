package co.edu.unicauca.cameia.perfil.presentation.dto;

/** Body del POST /api/v1/profiles/{id}/skills (CM-19). */
public record AddSkillRequest(String skillName, String level, String provenance) {
}
