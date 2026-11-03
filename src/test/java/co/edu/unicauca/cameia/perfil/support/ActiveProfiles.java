package co.edu.unicauca.cameia.perfil.support;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationDraft;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.SkillName;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Perfiles ya reconstituidos con datos literales, para probar lo que se anuncia sin pasar por la base. */
public final class ActiveProfiles {

    /** Identificador fijo del perfil de ejemplo. */
    public static final UUID PROFILE_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    /** Dueño fijo del perfil de ejemplo. */
    public static final String OWNER = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    /** Rol del catálogo usado por el perfil de ejemplo. */
    public static final UUID BACKEND_ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private ActiveProfiles() {
    }

    /**
     * Perfil del ejemplo del contrato: una experiencia vigente, una formación, una habilidad y un rol.
     *
     * @return el perfil en {@link ProfileStatus#COMPLETED}
     */
    public static ProfessionalProfile example() {
        return build(ProfileStatus.COMPLETED, "Analista de datos", "Analista con tres años de experiencia en SQL y Python.",
                List.of(new TargetRole(UUID.randomUUID(), BACKEND_ROLE, "Desarrollador Backend", DataProvenance.MANUAL)),
                List.of(experience("ACME", "Analista", null, YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT)),
                List.of(education("Universidad del Cauca", "Ingeniería de Sistemas", YearMonth.of(2017, 2), YearMonth.of(2021, 12))),
                List.of(skill("Python", SkillLevel.INTERMEDIATE)));
    }

    /**
     * Perfil en el estado indicado con los mismos datos del ejemplo.
     *
     * @param status estado del perfil
     * @return el perfil reconstituido en ese estado
     */
    public static ProfessionalProfile withStatus(ProfileStatus status) {
        return build(status, "Analista de datos", "Analista con tres años de experiencia en SQL y Python.",
                List.of(new TargetRole(UUID.randomUUID(), BACKEND_ROLE, "Desarrollador Backend", DataProvenance.MANUAL)),
                List.of(), List.of(education("Universidad del Cauca", "Ingeniería", YearMonth.of(2017, 2), null)),
                List.of(skill("Python", SkillLevel.INTERMEDIATE)));
    }

    /**
     * Perfil activo con nombre y resumen a medida y las colecciones indicadas.
     *
     * @param name        nombre tal como está guardado, puede ser {@code null}
     * @param summary     resumen tal como está guardado, puede ser {@code null}
     * @param roles       roles objetivo
     * @param experiences experiencias laborales
     * @param educations  formaciones
     * @param skills      habilidades
     * @return el perfil en {@link ProfileStatus#COMPLETED}
     */
    public static ProfessionalProfile active(String name, String summary, List<TargetRole> roles,
            List<WorkExperience> experiences, List<Education> educations, List<ProfileSkill> skills) {
        return build(ProfileStatus.COMPLETED, name, summary, roles, experiences, educations, skills);
    }

    /**
     * Experiencia laboral guardada.
     *
     * @param company     empresa
     * @param position    cargo
     * @param description descripción o {@code null}
     * @param start       mes de inicio
     * @param end         mes de fin o {@code null}
     * @param status      situación laboral
     * @return la experiencia reconstruida
     */
    public static WorkExperience experience(String company, String position, String description, YearMonth start,
            YearMonth end, EmploymentStatus status) {
        return WorkExperience.rebuild(UUID.randomUUID(),
                new WorkExperienceDraft(company, position, description, start, end, status), DataProvenance.MANUAL);
    }

    /**
     * Formación guardada de nivel universitario y no en curso.
     *
     * @param institution institución
     * @param degree      título
     * @param start       mes de inicio (obligatorio en una formación)
     * @param end         mes de fin o {@code null}
     * @return la formación reconstruida
     */
    public static Education education(String institution, String degree, YearMonth start, YearMonth end) {
        return Education.rebuild(UUID.randomUUID(),
                new EducationDraft(institution, degree, EducationLevel.UNDERGRADUATE, start, end, false), DataProvenance.MANUAL);
    }

    /**
     * Habilidad guardada.
     *
     * @param name  nombre tal como está guardado
     * @param level nivel
     * @return la habilidad
     */
    public static ProfileSkill skill(String name, SkillLevel level) {
        return new ProfileSkill(UUID.randomUUID(), new SkillName(name), level, DataProvenance.MANUAL);
    }

    private static ProfessionalProfile build(ProfileStatus status, String name, String summary, List<TargetRole> roles,
            List<WorkExperience> experiences, List<Education> educations, List<ProfileSkill> skills) {
        return ProfessionalProfile.reconstitute(ProfileId.of(PROFILE_ID), new FirebaseUid(OWNER),
                name == null ? null : new ProfileName(name), summary == null ? null : new ProfessionalSummary(summary),
                null, null, DataProvenance.MANUAL, status, ReviewStatus.PENDING_REVIEW, NOW, NOW,
                roles, experiences, educations, skills);
    }
}
