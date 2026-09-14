package co.edu.unicauca.cameia.perfil.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** Body del POST /api/v1/profiles/{id}/skills (CM-19). */
public record AddSkillRequest(
        @NotBlank String skillName,
        @NotBlank String level,
        @NotBlank String provenance) {
}
