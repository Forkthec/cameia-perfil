package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationNotAllowedException;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotAllowedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Agregado raíz del contexto de Perfil Profesional (glosario §6.2).
 *
 * <p>Concentra todas las invariantes de negocio de CM-16 a CM-23. Ninguna capa externa
 * puede mutar el estado directamente: todo pasa por los métodos de este agregado.
 * Los getters de colecciones retornan vistas no modificables.
 */
public final class ProfessionalProfile {

    public static final int MAX_TARGET_ROLES = 5;
    /** Máximo de experiencias laborales por perfil. */
    public static final int MAX_WORK_EXPERIENCES = 4;
    /** Máximo de formaciones académicas por perfil. */
    public static final int MAX_EDUCATIONS = 5;
    /** Máximo de habilidades por perfil. */
    public static final int MAX_SKILLS = 20;

    private final ProfileId id;
    private final FirebaseUid firebaseUid;
    private ProfileName name;
    private ProfessionalSummary summary;
    private SalaryExpectation salaryExpectation;
    private WorkModality preferredModality;
    private DataProvenance provenance;
    private ProfileStatus status;
    private ReviewStatus reviewStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private final List<TargetRole> targetRoles;
    private final List<WorkExperience> workExperiences;
    private final List<Education> educations;
    private final List<ProfileSkill> profileSkills;

    private ProfessionalProfile(ProfileId id, FirebaseUid firebaseUid) {
        this.id = Objects.requireNonNull(id);
        this.firebaseUid = Objects.requireNonNull(firebaseUid);
        this.status = ProfileStatus.IN_PROGRESS;
        this.reviewStatus = ReviewStatus.PENDING_REVIEW;
        this.provenance = DataProvenance.MANUAL;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.targetRoles = new ArrayList<>();
        this.workExperiences = new ArrayList<>();
        this.educations = new ArrayList<>();
        this.profileSkills = new ArrayList<>();
    }

    /** Crea un perfil nuevo en estado IN_PROGRESS. Punto de entrada de CM-16. */
    public static ProfessionalProfile create(FirebaseUid firebaseUid) {
        return new ProfessionalProfile(ProfileId.generate(), firebaseUid);
    }

    /** Reconstituye el agregado desde persistencia (uso exclusivo del adaptador). */
    public static ProfessionalProfile reconstitute(
            ProfileId id, FirebaseUid firebaseUid, ProfileName name,
            ProfessionalSummary summary, SalaryExpectation salaryExpectation,
            WorkModality preferredModality, DataProvenance provenance,
            ProfileStatus status, ReviewStatus reviewStatus,
            Instant createdAt, Instant updatedAt,
            List<TargetRole> targetRoles, List<WorkExperience> workExperiences,
            List<Education> educations, List<ProfileSkill> profileSkills) {
        ProfessionalProfile p = new ProfessionalProfile(id, firebaseUid);
        p.name = name;
        p.summary = summary;
        p.salaryExpectation = salaryExpectation;
        p.preferredModality = preferredModality;
        p.provenance = Objects.requireNonNull(provenance);
        p.status = Objects.requireNonNull(status);
        p.reviewStatus = Objects.requireNonNull(reviewStatus);
        p.createdAt = Objects.requireNonNull(createdAt);
        p.updatedAt = Objects.requireNonNull(updatedAt);
        p.targetRoles.addAll(targetRoles);
        p.workExperiences.addAll(workExperiences);
        p.educations.addAll(educations);
        p.profileSkills.addAll(profileSkills);
        return p;
    }

    // ── Información general ──────────────────────────────────────
    public void updateName(ProfileName name) { this.name = name; touch(); }
    /** @param summary resumen nuevo, o {@code null} para borrarlo (CA-2.3.4); un perfil activo no lo pierde (CA-2.3.12) */
    public void updateSummary(ProfessionalSummary summary) {
        if (summary == null && status == ProfileStatus.COMPLETED) {
            throw InvalidFieldsException.of("summary", ErrorCode.SUMMARY_NOT_ALLOWED,
                    "No puedes quedarte sin resumen profesional con el perfil activo.");
        }
        this.summary = summary; touch();
    }
    public void updatePreferredModality(WorkModality modality) { this.preferredModality = modality; touch(); }

    // ── Expectativa salarial (CM-19 — dormido en MVP, ver BE-14) ────────
    public void updateSalaryExpectation(SalaryExpectation expectation) { this.salaryExpectation = expectation; touch(); }

    // ── Roles objetivo (CM-20 / CM-23) ──────────────────────────────────
    public void addTargetRole(TargetRole role) {
        Objects.requireNonNull(role);
        if (targetRoles.size() >= MAX_TARGET_ROLES) throw new MaxTargetRolesExceededException(MAX_TARGET_ROLES);
        if (targetRoles.stream().anyMatch(r -> r.isSameRoleAs(role))) throw new DuplicateTargetRoleException();
        targetRoles.add(role);
        touch();
    }

    public void removeTargetRole(UUID roleId) {
        Objects.requireNonNull(roleId);
        if (targetRoles.stream().noneMatch(r -> r.getId().equals(roleId))) throw new TargetRoleNotFoundException();
        // Solo bloquea el último rol cuando el perfil ya está COMPLETED (BE-08)
        if (this.status == ProfileStatus.COMPLETED && targetRoles.size() == 1) throw new LastTargetRoleException();
        targetRoles.removeIf(r -> r.getId().equals(roleId));
        touch();
    }

    /**
     * Sustituye el rol del catálogo de un rol objetivo; conserva su identificador, su procedencia y su lugar en la lista.
     *
     * @param roleId             identificador del rol objetivo a sustituir
     * @param professionalRoleId nuevo rol del catálogo; si es nulo se conserva el actual
     * @param roleTitle          título del nuevo rol; si es nulo se conserva el actual
     * @throws TargetRoleNotFoundException  si el rol objetivo no está en el perfil
     * @throws DuplicateTargetRoleException si el nuevo rol ya está en otro rol objetivo del perfil
     */
    public void updateTargetRole(UUID roleId, UUID professionalRoleId, String roleTitle) {
        Objects.requireNonNull(roleId);
        int index = IntStream.range(0, targetRoles.size())
                .filter(i -> targetRoles.get(i).getId().equals(roleId))
                .findFirst().orElseThrow(TargetRoleNotFoundException::new);
        TargetRole existing = targetRoles.get(index);
        var replacement = new TargetRole(roleId,
                professionalRoleId != null ? professionalRoleId : existing.getProfessionalRoleId(),
                roleTitle != null ? roleTitle : existing.getRoleTitle(),
                existing.getProvenance());
        // CA-2.11.8: sustituir por un rol que ya está en el perfil se rechaza.
        if (targetRoles.stream().anyMatch(r -> !r.getId().equals(roleId) && r.isSameRoleAs(replacement))) {
            throw new DuplicateTargetRoleException();
        }
        // Sustituir conserva el Rol Objetivo y su lugar en la lista: el backlog no permite reordenar.
        targetRoles.set(index, replacement);
        touch();
    }

    // ── Experiencia y educación (CM-18) ──────────────────────────────────
    /**
     * Agrega una experiencia laboral.
     *
     * @param exp experiencia ya validada
     * @throws WorkExperienceLimitReachedException si el perfil ya tiene {@value #MAX_WORK_EXPERIENCES}
     */
    public void addWorkExperience(WorkExperience exp) {
        Objects.requireNonNull(exp);
        if (workExperiences.size() >= MAX_WORK_EXPERIENCES) {
            throw new WorkExperienceLimitReachedException();
        }
        workExperiences.add(exp);
        touch();
    }
    public void removeWorkExperience(UUID expId) {
        removeById(workExperiences, e -> e.getId().equals(expId), WorkExperienceNotFoundException::new);
    }

    /**
     * Agrega una formación académica.
     *
     * @param edu formación ya validada
     * @throws EducationLimitReachedException si el perfil ya tiene {@value #MAX_EDUCATIONS}
     */
    public void addEducation(Education edu) {
        Objects.requireNonNull(edu);
        if (educations.size() >= MAX_EDUCATIONS) {
            throw new EducationLimitReachedException();
        }
        educations.add(edu);
        touch();
    }

    /**
     * Elimina una formación de este perfil.
     *
     * <p>Un perfil Activo debe conservar al menos una formación: la comprobación y la eliminación ocurren
     * con la fila del perfil bloqueada, así que dos pestañas que eliminan las dos últimas no pueden tener
     * éxito las dos. Un identificador ajeno responde «no encontrado» antes que «no permitido».</p>
     *
     * @param eduId identificador de la formación
     * @throws EducationNotFoundException   si el identificador no es una formación de este perfil
     * @throws EducationNotAllowedException si es la única formación de un perfil Activo
     */
    public void removeEducation(UUID eduId) {
        if (educations.stream().noneMatch(e -> e.getId().equals(eduId))) {
            throw new EducationNotFoundException();
        }
        if (status == ProfileStatus.COMPLETED && educations.size() == 1) {
            throw new EducationNotAllowedException();
        }
        educations.removeIf(e -> e.getId().equals(eduId));
        touch();
    }

    // ── Habilidades ──────────────────────────────────────────────────────
    /**
     * Agrega una habilidad. El máximo se comprueba primero: con {@value #MAX_SKILLS} no se acepta ninguna,
     * repetida o no.
     *
     * @param skill habilidad que se agrega
     * @throws SkillLimitReachedException si el perfil ya tiene el máximo
     * @throws DuplicateSkillException    si el perfil ya tiene la misma habilidad
     */
    public void addSkill(ProfileSkill skill) {
        Objects.requireNonNull(skill);
        if (profileSkills.size() >= MAX_SKILLS) {
            throw new SkillLimitReachedException();
        }
        if (profileSkills.stream().anyMatch(s -> s.getName().sameAs(skill.getName()))) {
            throw new DuplicateSkillException();
        }
        profileSkills.add(skill);
        touch();
    }

    /**
     * Elimina una habilidad; un perfil Activo conserva al menos una. Las dos comprobaciones corren con la
     * fila del perfil bloqueada, así una finalización en otra pestaña no deja un perfil Activo sin habilidades.
     * Un identificador ajeno responde «no encontrado» antes que «no permitido».
     *
     * @param skillId identificador de la habilidad
     * @throws SkillNotFoundException   si el identificador no es una habilidad de este perfil
     * @throws SkillNotAllowedException si es la única habilidad de un perfil Activo
     */
    public void removeSkill(UUID skillId) {
        if (profileSkills.stream().noneMatch(s -> s.getId().equals(skillId))) {
            throw new SkillNotFoundException();
        }
        if (status == ProfileStatus.COMPLETED && profileSkills.size() == 1) {
            throw new SkillNotAllowedException();
        }
        profileSkills.removeIf(s -> s.getId().equals(skillId));
        touch();
    }

    /** Quita el elemento del perfil, o lanza el «no encontrado» si no es de este perfil (CA-2.4.58, 2.4.59, 2.5.23). */
    private <T> void removeById(List<T> items, Predicate<T> matches, Supplier<? extends RuntimeException> notFound) {
        if (!items.removeIf(matches)) throw notFound.get();
        touch();
    }

    // ── Transiciones de estado ───────────────────────────────────────────

    /** Flujo futuro (IA + revisión humana). Conservado en CM-04/BE-04. */
    public void requestReview() {
        if (!isComplete()) throw new IncompleteProfileException(getMissingRequirements());
        this.status = ProfileStatus.IN_REVIEW;
        touch();
    }

    /** Flujo manual MVP (CM-22): IN_PROGRESS → COMPLETED en una sola transacción. */
    public void complete() {
        if (this.status == ProfileStatus.COMPLETED) throw new ProfileAlreadyCompletedException();
        List<String> missing = getMissingRequirements();
        if (!missing.isEmpty()) throw new IncompleteProfileException(missing);
        this.status = ProfileStatus.COMPLETED;
        touch();
    }

    public void markAsReviewed() { this.status = ProfileStatus.COMPLETED; this.reviewStatus = ReviewStatus.REVIEWED; touch(); }
    public void returnForRevision() { this.status = ProfileStatus.IN_PROGRESS; touch(); }

    public List<String> getMissingRequirements() {
        List<String> missing = new ArrayList<>();
        if (name == null || name.value().isBlank()) missing.add("NAME");
        if (summary == null) missing.add("SUMMARY");
        if (educations.isEmpty()) missing.add("EDUCATION");
        if (profileSkills.isEmpty()) missing.add("SKILLS");
        if (targetRoles.isEmpty()) missing.add("TARGET_ROLES");
        return missing;
    }

    public boolean isComplete() {
        return getMissingRequirements().isEmpty();
    }

    // ── Getters ──────────────────────────────────────────────────────────
    public ProfileId getId() { return id; }
    public FirebaseUid getFirebaseUid() { return firebaseUid; }
    public ProfileName getName() { return name; }
    public ProfessionalSummary getSummary() { return summary; }
    public SalaryExpectation getSalaryExpectation() { return salaryExpectation; }
    public WorkModality getPreferredModality() { return preferredModality; }
    public DataProvenance getProvenance() { return provenance; }
    public ProfileStatus getStatus() { return status; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<TargetRole> getTargetRoles() { return Collections.unmodifiableList(targetRoles); }
    public List<WorkExperience> getWorkExperiences() { return Collections.unmodifiableList(workExperiences); }
    public List<Education> getEducations() { return Collections.unmodifiableList(educations); }
    public List<ProfileSkill> getProfileSkills() { return Collections.unmodifiableList(profileSkills); }

    private void touch() { this.updatedAt = Instant.now(); }
}
