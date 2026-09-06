package co.edu.unicauca.cameia.perfil.presentation.dto;

/** Body del POST /api/v1/profiles/{id}/work-experiences (CM-18). */
public record AddWorkExperienceRequest(
        String company,
        String position,
        String description,
        String startDate,
        String endDate,
        String employmentStatus,
        String seniority,
        String provenance) {
}
