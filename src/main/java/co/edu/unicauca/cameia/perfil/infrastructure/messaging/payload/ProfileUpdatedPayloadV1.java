package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cuerpo JSON de {@code perfil.profesional-actualizado} versión 1. Los nombres de campo los fija el contrato publicado.
 * Todo valor es texto, salvo {@code enCurso} y las listas, para que el formato en el cable no dependa del serializador;
 * los valores opcionales viajan como {@code null}. No lleva {@code embedding}: lo calcula Entrevista.
 *
 * @param userId              {@code firebaseUid} del dueño del perfil
 * @param professionalContext copia del perfil
 * @param targetRoles         roles objetivo del perfil, en su orden
 * @param updatedAt           instante del cambio en UTC con milisegundos y sufijo {@code Z}
 */
public record ProfileUpdatedPayloadV1(
        @JsonProperty("usuarioId") String userId,
        @JsonProperty("contextoProfesional") ProfessionalContext professionalContext,
        @JsonProperty("rolesAspirados") List<TargetRoleItem> targetRoles,
        @JsonProperty("actualizadoEn") String updatedAt) {

    private static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Construye la carga a partir del evento de dominio.
     *
     * @param event evento con el perfil activo ya modificado
     * @return la carga del contrato v1
     */
    public static ProfileUpdatedPayloadV1 from(ProfileUpdated event) {
        var profile = event.profile();
        return new ProfileUpdatedPayloadV1(profile.getFirebaseUid().value(), contextOf(profile),
                profile.getTargetRoles().stream().map(ProfileUpdatedPayloadV1::roleOf).toList(),
                INSTANT_FORMAT.format(event.updatedAt()));
    }

    /**
     * Copia los datos del perfil al bloque {@code contextoProfesional}.
     *
     * @param profile perfil activo
     * @return el bloque con listas en el orden del perfil
     */
    private static ProfessionalContext contextOf(ProfessionalProfile profile) {
        // Una fila antigua puede no tener nombre ni resumen: viajan como null en lugar de romper la publicación.
        return new ProfessionalContext(profile.getId().value().toString(),
                profile.getName() == null ? null : profile.getName().value(), profile.getStatus().name(),
                null, profile.getSummary() == null ? null : profile.getSummary().value(),
                profile.getWorkExperiences().stream().map(ProfileUpdatedPayloadV1::experienceOf).toList(),
                profile.getEducations().stream().map(ProfileUpdatedPayloadV1::educationOf).toList(),
                profile.getProfileSkills().stream().map(ProfileUpdatedPayloadV1::skillOf).toList());
    }

    /**
     * Copia una experiencia laboral; las fechas viajan como {@code AAAA-MM} o {@code null}.
     *
     * @param e experiencia del perfil
     * @return el elemento de {@code experiencias[]}
     */
    private static WorkExperienceItem experienceOf(WorkExperience e) {
        return new WorkExperienceItem(e.getCompany(), e.getPosition(), e.getDescription(), month(e.getStartDate()),
                month(e.getEndDate()), e.getEmploymentStatus().name());
    }

    /**
     * Copia una formación académica; las fechas viajan como {@code AAAA-MM} o {@code null}.
     *
     * @param e formación del perfil
     * @return el elemento de {@code formaciones[]}
     */
    private static EducationItem educationOf(Education e) {
        return new EducationItem(e.getInstitution(), e.getDegree(), e.getLevel().name(), month(e.getStartDate()),
                month(e.getEndDate()), e.isInProgress());
    }

    /**
     * Copia una habilidad con el nombre del enumerado de su nivel.
     *
     * @param s habilidad del perfil
     * @return el elemento de {@code habilidades[]}
     */
    private static SkillItem skillOf(ProfileSkill s) {
        return new SkillItem(s.getSkillName(), s.getLevel().name());
    }

    /**
     * Copia un rol objetivo; el código es el identificador del rol en el catálogo.
     *
     * @param r rol objetivo del perfil
     * @return el elemento de {@code rolesAspirados[]}
     */
    private static TargetRoleItem roleOf(TargetRole r) {
        return new TargetRoleItem(r.getProfessionalRoleId().toString(), r.getRoleTitle());
    }

    /**
     * Da el mes en texto {@code AAAA-MM}.
     *
     * @param month mes o {@code null}
     * @return el texto del mes, o {@code null} si no hay mes
     */
    private static String month(YearMonth month) {
        return month == null ? null : month.toString();
    }

    /** Datos del perfil (JSON {@code contextoProfesional}); {@code fecha_archivado} siempre es nulo mientras no exista el archivado. */
    public record ProfessionalContext(@JsonProperty("id") String id, @JsonProperty("nombre") String name,
            @JsonProperty("estado") String status, @JsonProperty("fecha_archivado") String archivedAt,
            @JsonProperty("resumen") String summary, @JsonProperty("experiencias") List<WorkExperienceItem> workExperiences,
            @JsonProperty("formaciones") List<EducationItem> educations, @JsonProperty("habilidades") List<SkillItem> skills) {
    }

    /** Una experiencia laboral (JSON {@code experiencias[]}). */
    public record WorkExperienceItem(@JsonProperty("empresa") String company, @JsonProperty("cargo") String position,
            @JsonProperty("descripcion") String description, @JsonProperty("inicio") String start,
            @JsonProperty("fin") String end, @JsonProperty("estadoEmpleo") String employmentStatus) {
    }

    /** Una formación académica (JSON {@code formaciones[]}). */
    public record EducationItem(@JsonProperty("institucion") String institution, @JsonProperty("titulo") String degree,
            @JsonProperty("nivel") String level, @JsonProperty("inicio") String start, @JsonProperty("fin") String end,
            @JsonProperty("enCurso") boolean inProgress) {
    }

    /** Una habilidad (JSON {@code habilidades[]}). */
    public record SkillItem(@JsonProperty("nombre") String name, @JsonProperty("nivel") String level) {
    }

    /** Un rol objetivo (JSON {@code rolesAspirados[]}); el código es el id del rol en el catálogo de Perfil. */
    public record TargetRoleItem(@JsonProperty("codigo") String code, @JsonProperty("nombre") String name) {
    }
}
