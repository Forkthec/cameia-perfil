package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Agregado raíz del contexto de Perfil Profesional (glosario §6.2).
 *
 * <p>Concentra todas las invariantes de negocio de CM-16 a CM-23. Ninguna capa externa
 * puede mutar el estado directamente: todo pasa por los métodos de este agregado.
 * Los getters de colecciones retornan vistas no modificables.
 */
public final class ProfessionalProfile {

    public static final int MAX_TARGET_ROLES = 5;

    private final ProfileId id;
    private final FirebaseUid firebaseUid;
    private ProfileName name;
    private ProfessionalSummary summary;
    private SalaryExpectation salaryExpectation;
    private WorkModality preferredModality;
    private DataProvenance provenance;
    private ProfileStatus status;
    private ReviewStatus reviewStatus;
    private final Instant createdAt;
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
        p.updatedAt = Objects.requireNonNull(updatedAt);
        p.targetRoles.addAll(targetRoles);
        p.workExperiences.addAll(workExperiences);
        p.educations.addAll(educations);
        p.profileSkills.addAll(profileSkills);
        return p;
    }

    // ── Información general (CM-17) ──────────────────────────────────────
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
    public void updateProvenance(DataProvenance provenance) { this.provenance = Objects.requireNonNull(provenance); touch(); }

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

    public void updateTargetRole(UUID roleId, UUID professionalRoleId, String roleTitle) {
        Objects.requireNonNull(roleId);
        TargetRole existing = targetRoles.stream().filter(r -> r.getId().equals(roleId))
                .findFirst().orElseThrow(TargetRoleNotFoundException::new);
        var replacement = new TargetRole(roleId,
                professionalRoleId != null ? professionalRoleId : existing.getProfessionalRoleId(),
                roleTitle != null ? roleTitle : existing.getRoleTitle(),
                existing.getProvenance());
        // CA-2.11.8: sustituir por un rol que ya está en el perfil se rechaza.
        if (targetRoles.stream().anyMatch(r -> !r.getId().equals(roleId) && r.isSameRoleAs(replacement))) {
            throw new DuplicateTargetRoleException();
        }
        targetRoles.removeIf(r -> r.getId().equals(roleId));
        targetRoles.add(replacement);
        touch();
    }

    // ── Experiencia y educación (CM-18) ──────────────────────────────────
    public void addWorkExperience(WorkExperience exp) { workExperiences.add(Objects.requireNonNull(exp)); touch(); }
    public void removeWorkExperience(UUID expId) {
        removeById(workExperiences, e -> e.getId().equals(expId), WorkExperienceNotFoundException::new);
    }
    public void addEducation(Education edu) { educations.add(Objects.requireNonNull(edu)); touch(); }
    public void removeEducation(UUID eduId) {
        removeById(educations, e -> e.getId().equals(eduId), EducationNotFoundException::new);
    }

    // ── Habilidades (CM-19) ──────────────────────────────────────────────
    public void addSkill(ProfileSkill skill) {
        Objects.requireNonNull(skill);
        String normalizado = normalizarNombreHabilidad(skill.getSkillName());
        boolean duplicada = profileSkills.stream()
                .anyMatch(s -> normalizarNombreHabilidad(s.getSkillName()).equals(normalizado));
        if (duplicada) throw new DuplicateSkillException();
        profileSkills.add(skill);
        touch();
    }

    private static String normalizarNombreHabilidad(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
    public void removeSkill(UUID skillId) {
        removeById(profileSkills, s -> s.getId().equals(skillId), SkillNotFoundException::new);
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
