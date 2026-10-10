package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Representación HTTP de un perfil profesional.
 * El método estático {@code from} hace el mapping desde el agregado de dominio.
 */
public record ProfileResponse(
        UUID id,
        String status,
        String reviewStatus,
        String provenance,
        String name,
        String summary,
        BigDecimal salaryExpectation,
        String preferredModality,
        Instant createdAt,
        Instant updatedAt,
        List<TargetRoleItem> targetRoles,
        List<WorkExperienceItem> workExperiences,
        List<EducationItem> educations,
        List<ProfileSkillItem> profileSkills
) {

    public static ProfileResponse from(ProfessionalProfile p) {
        return new ProfileResponse(
                p.getId().value(),
                p.getStatus().name(),
                p.getReviewStatus().name(),
                p.getProvenance().name(),
                p.getName() != null ? p.getName().value() : null,
                p.getSummary() != null ? p.getSummary().value() : null,
                p.getSalaryExpectation() != null ? p.getSalaryExpectation().amount() : null,
                p.getPreferredModality() != null ? p.getPreferredModality().name() : null,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getTargetRoles().stream().map(TargetRoleItem::from).toList(),
                p.getWorkExperiences().stream().map(WorkExperienceItem::from).toList(),
                p.getEducations().stream().map(EducationItem::from).toList(),
                p.getProfileSkills().stream().map(ProfileSkillItem::from).toList()
        );
    }

    /** CM-21/CM-23: seniority eliminado; profesionalRoleId y roleTitle reemplazan title. */
    public record TargetRoleItem(
            @Schema(description = "Identificador del rol objetivo dentro del perfil", format = "uuid",
                    example = "7d1f6a52-3c0b-4e8a-9b14-2f5d8c6e0a31")
            UUID id,
            @Schema(description = "Rol del catálogo al que apunta", format = "uuid",
                    example = "a0000001-0000-0000-0000-000000000002")
            UUID professionalRoleId,
            @Schema(description = "Nombre del rol tal como se guardó", example = "Desarrollador Backend")
            String roleTitle,
            @Schema(description = "Procedencia del dato", allowableValues = {"MANUAL", "AI_SUGGESTED", "AI_EDITED"},
                    example = "MANUAL")
            String provenance) {
        static TargetRoleItem from(TargetRole r) {
            return new TargetRoleItem(r.getId(), r.getProfessionalRoleId(), r.getRoleTitle(), r.getProvenance().name());
        }
    }

    /** Experiencia laboral del perfil, tal como se guardó. */
    public record WorkExperienceItem(
            @Schema(description = "Identificador de la experiencia") UUID id,
            @Schema(description = "Empresa", example = "Bancolombia") String company,
            @Schema(description = "Cargo", example = "Desarrolladora Backend") String position,
            @Schema(description = "Descripción; null si no se escribió", example = "APIs REST con Spring Boot", nullable = true) String description,
            @Schema(description = "Mes de inicio AAAA-MM; null si no se indicó", example = "2024-06", nullable = true) String startDate,
            @Schema(description = "Mes de fin AAAA-MM; null mientras la experiencia sigue en curso", example = "2024-12", nullable = true) String endDate,
            @Schema(description = "Estado de la experiencia", example = "ENDED", allowableValues = {"CURRENT", "ENDED", "UNKNOWN_END"}) String employmentStatus,
            @Schema(description = "Procedencia del dato", example = "MANUAL") String provenance) {
        static WorkExperienceItem from(WorkExperience e) {
            return new WorkExperienceItem(
                    e.getId(), e.getCompany(), e.getPosition(), e.getDescription(),
                    e.getStartDate() != null ? formatYearMonth(e.getStartDate()) : null,
                    e.getEndDate() != null ? formatYearMonth(e.getEndDate()) : null,
                    e.getEmploymentStatus().name(), e.getProvenance().name());
        }

        private static String formatYearMonth(YearMonth ym) {
            return ym.toString(); // "YYYY-MM"
        }
    }

    public record EducationItem(
            UUID id, String institution, String degree, String fieldOfStudy,
            String level, String startDate, String endDate,
            boolean inProgress, String provenance) {
        static EducationItem from(Education e) {
            return new EducationItem(
                    e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                    e.getLevel().name(),
                    e.getStartDate().toString(),
                    e.getEndDate() != null ? e.getEndDate().toString() : null,
                    e.isInProgress(), e.getProvenance().name());
        }
    }

    public record ProfileSkillItem(UUID id, String skillName, String level, String provenance) {
        static ProfileSkillItem from(ProfileSkill s) {
            return new ProfileSkillItem(s.getId(), s.getSkillName(), s.getLevel().name(), s.getProvenance().name());
        }
    }
}
