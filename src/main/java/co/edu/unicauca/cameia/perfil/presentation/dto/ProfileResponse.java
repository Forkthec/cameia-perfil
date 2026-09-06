package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;

import java.math.BigDecimal;
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
        String headline,
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
                p.getHeadline(),
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

    public record TargetRoleItem(UUID id, String title, String seniority, String provenance) {
        static TargetRoleItem from(TargetRole r) {
            return new TargetRoleItem(r.getId(), r.getTitle(), r.getSeniority().name(), r.getProvenance().name());
        }
    }

    public record WorkExperienceItem(
            UUID id, String company, String position, String description,
            String startDate, String endDate, String employmentStatus,
            String seniority, String provenance) {
        static WorkExperienceItem from(WorkExperience e) {
            return new WorkExperienceItem(
                    e.getId(), e.getCompany(), e.getPosition(), e.getDescription(),
                    formatYearMonth(e.getStartDate()),
                    e.getEndDate() != null ? formatYearMonth(e.getEndDate()) : null,
                    e.getEmploymentStatus().name(), e.getSeniority().name(), e.getProvenance().name());
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
