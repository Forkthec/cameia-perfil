package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
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

/** CM-21: eliminada columna seniority. Desviación del C4: usa estado_empleo VARCHAR en lugar de actual BOOLEAN. */
@Entity
@Table(name = "experiencia_laboral")
public class WorkExperienceEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private ProfessionalProfileEntity profile;

    @Column(name = "empresa", nullable = false, length = 500)
    private String company;

    @Column(name = "cargo", nullable = false, length = 500)
    private String position;

    @Column(name = "descripcion", length = 2000)
    private String description;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate startDate;

    @Column(name = "fecha_fin")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_empleo", nullable = false, length = 20)
    private EmploymentStatus employmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedencia", nullable = false, length = 20)
    private DataProvenance provenance;

    public WorkExperienceEntity() {}

    public UUID getId() { return id; }
    public ProfessionalProfileEntity getProfile() { return profile; }
    public String getCompany() { return company; }
    public String getPosition() { return position; }
    public String getDescription() { return description; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public DataProvenance getProvenance() { return provenance; }

    public void setId(UUID id) { this.id = id; }
    public void setProfile(ProfessionalProfileEntity profile) { this.profile = profile; }
    public void setCompany(String company) { this.company = company; }
    public void setPosition(String position) { this.position = position; }
    public void setDescription(String description) { this.description = description; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setEmploymentStatus(EmploymentStatus employmentStatus) { this.employmentStatus = employmentStatus; }
    public void setProvenance(DataProvenance provenance) { this.provenance = provenance; }
}
