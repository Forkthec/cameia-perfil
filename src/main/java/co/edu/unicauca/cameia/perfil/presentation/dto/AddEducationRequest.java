package co.edu.unicauca.cameia.perfil.presentation.dto;

/** Body del POST /api/v1/profiles/{id}/educations (CM-18). */
public record AddEducationRequest(
        String institution,
        String degree,
        String fieldOfStudy,
        String level,
        String startDate,
        String endDate,
        boolean inProgress,
        String provenance) {
}
