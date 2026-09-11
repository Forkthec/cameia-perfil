package co.edu.unicauca.cameia.perfil.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body del POST /api/v1/profiles/{id}/work-experiences (CM-18). Seniority eliminado en CM-21. */
public record AddWorkExperienceRequest(
        @NotBlank String company,
        @NotBlank String position,
        String description,
        @NotBlank String startDate,
        String endDate,
        @NotNull String employmentStatus,
        @NotNull String provenance) {
}
