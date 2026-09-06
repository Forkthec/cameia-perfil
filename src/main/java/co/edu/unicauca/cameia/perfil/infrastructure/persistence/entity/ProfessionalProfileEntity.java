package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "perfil_profesional")
public class ProfessionalProfileEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    @Column(name = "nombre", length = 255)
    private String name;

    @Column(name = "titular", length = 255)
    private String headline;

    @Column(name = "resumen", length = 2000)
    private String summary;

    @Column(name = "expectativa_salarial", precision = 15, scale = 2)
    private BigDecimal salaryExpectation;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_preferida", length = 20)
    private WorkModality preferredModality;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedencia", nullable = false, length = 20)
    private DataProvenance provenance;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_revision", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private ProfileStatus status;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkExperienceEntity> workExperiences = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EducationEntity> educations = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TargetRoleEntity> targetRoles = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileSkillEntity> profileSkills = new ArrayList<>();

    public ProfessionalProfileEntity() {}

    public UUID getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public String getName() { return name; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public BigDecimal getSalaryExpectation() { return salaryExpectation; }
    public WorkModality getPreferredModality() { return preferredModality; }
    public DataProvenance getProvenance() { return provenance; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public ProfileStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<WorkExperienceEntity> getWorkExperiences() { return workExperiences; }
    public List<EducationEntity> getEducations() { return educations; }
    public List<TargetRoleEntity> getTargetRoles() { return targetRoles; }
    public List<ProfileSkillEntity> getProfileSkills() { return profileSkills; }

    public void setId(UUID id) { this.id = id; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }
    public void setName(String name) { this.name = name; }
    public void setHeadline(String headline) { this.headline = headline; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setSalaryExpectation(BigDecimal salaryExpectation) { this.salaryExpectation = salaryExpectation; }
    public void setPreferredModality(WorkModality preferredModality) { this.preferredModality = preferredModality; }
    public void setProvenance(DataProvenance provenance) { this.provenance = provenance; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
    public void setStatus(ProfileStatus status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setWorkExperiences(List<WorkExperienceEntity> workExperiences) { this.workExperiences = workExperiences; }
    public void setEducations(List<EducationEntity> educations) { this.educations = educations; }
    public void setTargetRoles(List<TargetRoleEntity> targetRoles) { this.targetRoles = targetRoles; }
    public void setProfileSkills(List<ProfileSkillEntity> profileSkills) { this.profileSkills = profileSkills; }
}
