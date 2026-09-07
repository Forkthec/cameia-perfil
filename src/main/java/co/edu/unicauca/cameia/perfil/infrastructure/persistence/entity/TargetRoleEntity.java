package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.model.Seniority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rol_objetivo")
public class TargetRoleEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private ProfessionalProfileEntity profile;

    @Column(name = "nombre_rol", nullable = false, length = 255)
    private String roleTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", nullable = false, length = 20)
    private Seniority seniority;

    @Column(name = "id_sugerencia_empleo")
    private UUID jobSuggestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedencia", nullable = false, length = 20)
    private DataProvenance provenance;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_revision", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    public TargetRoleEntity() {}

    public UUID getId() { return id; }
    public ProfessionalProfileEntity getProfile() { return profile; }
    public String getRoleTitle() { return roleTitle; }
    public Seniority getSeniority() { return seniority; }
    public UUID getJobSuggestionId() { return jobSuggestionId; }
    public DataProvenance getProvenance() { return provenance; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setProfile(ProfessionalProfileEntity profile) { this.profile = profile; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
    public void setSeniority(Seniority seniority) { this.seniority = seniority; }
    public void setJobSuggestionId(UUID jobSuggestionId) { this.jobSuggestionId = jobSuggestionId; }
    public void setProvenance(DataProvenance provenance) { this.provenance = provenance; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

