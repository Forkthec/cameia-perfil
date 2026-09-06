package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "educacion")
public class EducationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private ProfessionalProfileEntity profile;

    @Column(name = "institucion", nullable = false, length = 500)
    private String institution;

    @Column(name = "titulo", nullable = false, length = 500)
    private String degree;

    @Column(name = "area_estudio", length = 500)
    private String fieldOfStudy;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 20)
    private EducationLevel level;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate startDate;

    @Column(name = "fecha_fin")
    private LocalDate endDate;

    @Column(name = "en_curso", nullable = false)
    private boolean inProgress;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedencia", nullable = false, length = 20)
    private DataProvenance provenance;

    public EducationEntity() {}

    public UUID getId() { return id; }
    public ProfessionalProfileEntity getProfile() { return profile; }
    public String getInstitution() { return institution; }
    public String getDegree() { return degree; }
    public String getFieldOfStudy() { return fieldOfStudy; }
    public EducationLevel getLevel() { return level; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isInProgress() { return inProgress; }
    public DataProvenance getProvenance() { return provenance; }

    public void setId(UUID id) { this.id = id; }
    public void setProfile(ProfessionalProfileEntity profile) { this.profile = profile; }
    public void setInstitution(String institution) { this.institution = institution; }
    public void setDegree(String degree) { this.degree = degree; }
    public void setFieldOfStudy(String fieldOfStudy) { this.fieldOfStudy = fieldOfStudy; }
    public void setLevel(EducationLevel level) { this.level = level; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setInProgress(boolean inProgress) { this.inProgress = inProgress; }
    public void setProvenance(DataProvenance provenance) { this.provenance = provenance; }
}
