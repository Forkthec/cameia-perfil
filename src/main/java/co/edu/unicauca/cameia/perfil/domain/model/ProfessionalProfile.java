package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    public void updateSummary(ProfessionalSummary summary) { this.summary = summary; touch(); }
    public void updatePreferredModality(WorkModality modality) { this.preferredModality = modality; touch(); }
    public void updateProvenance(DataProvenance provenance) { this.provenance = Objects.requireNonNull(provenance); touch(); }

    // ── Expectativa salarial (CM-19 — dormido en MVP, ver BE-14) ────────
    public void updateSalaryExpectation(SalaryExpectation expectation) { this.salaryExpectation = expectation; touch(); }

    // ── Roles objetivo (CM-20 / CM-23) ──────────────────────────────────
    public void addTargetRole(TargetRole role) {
        Objects.requireNonNull(role);
        if (targetRoles.size() >= MAX_TARGET_ROLES) throw new MaxTargetRolesExceededException(MAX_TARGET_ROLES);
        if (targetRoles.stream().anyMatch(r -> r.isSameRoleAs(role))) throw new DuplicateTargetRoleException(role.getRoleTitle());
        targetRoles.add(role);
        touch();
    }

    public void removeTargetRole(UUID roleId) {
        // Solo bloquea el último rol cuando el perfil ya está COMPLETED (BE-08)
        if (this.status == ProfileStatus.COMPLETED && targetRoles.size() == 1) throw new LastTargetRoleException();
        targetRoles.removeIf(r -> r.getId().equals(Objects.requireNonNull(roleId)));
        touch();
    }

    public void updateTargetRole(UUID roleId, UUID professionalRoleId, String roleTitle) {
        Objects.requireNonNull(roleId);
        TargetRole existing = targetRoles.stream().filter(r -> r.getId().equals(roleId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Rol objetivo no encontrado: " + roleId));
        DataProvenance prov = existing.getProvenance();
        targetRoles.removeIf(r -> r.getId().equals(roleId));
        targetRoles.add(new TargetRole(roleId,
                professionalRoleId != null ? professionalRoleId : existing.getProfessionalRoleId(),
                roleTitle != null ? roleTitle : existing.getRoleTitle(),
                prov));
        touch();
    }

    // ── Experiencia y educación (CM-18) ──────────────────────────────────
    public void addWorkExperience(WorkExperience exp) { workExperiences.add(Objects.requireNonNull(exp)); touch(); }
    public void removeWorkExperience(UUID expId) { workExperiences.removeIf(e -> e.getId().equals(expId)); touch(); }
    public void addEducation(Education edu) { educations.add(Objects.requireNonNull(edu)); touch(); }
    public void removeEducation(UUID eduId) { educations.removeIf(e -> e.getId().equals(eduId)); touch(); }

    // ── Habilidades (CM-19) ──────────────────────────────────────────────
    public void addSkill(ProfileSkill skill) { profileSkills.add(Objects.requireNonNull(skill)); touch(); }
    public void removeSkill(UUID skillId) { profileSkills.removeIf(s -> s.getId().equals(skillId)); touch(); }

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
        if (name == null || name.value().isBlank()) missing.add("name");
        if (summary == null || summary.value().isBlank()) missing.add("summary");
        if (educations.isEmpty()) missing.add("al menos 1 educación");
        if (profileSkills.isEmpty()) missing.add("al menos 1 habilidad");
        if (targetRoles.isEmpty()) missing.add("al menos 1 rol objetivo");
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
