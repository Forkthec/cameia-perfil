package co.edu.unicauca.cameia.perfil.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** Body del POST /api/v1/profiles/{id}/educations (CM-18). */
public record AddEducationRequest(
        @NotBlank String institution,
        @NotBlank String degree,
        String fieldOfStudy,
        @NotBlank String level,
        @NotBlank String startDate,
        String endDate,
        /** Ausente cuenta como {@code false}: la formación no está en curso. */
        Boolean inProgress,
        @NotBlank String provenance) {
}
