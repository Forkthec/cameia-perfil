package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * DesviaciÃ³n del C4: almacena nombre_habilidad como texto en lugar de FK a tabla habilidad
 * (catÃ¡logo no implementado en Sprint 1).
 */
@Entity
@Table(name = "habilidad_perfil")
public class ProfileSkillEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private ProfessionalProfileEntity profile;

    @Column(name = "nombre_habilidad", nullable = false, length = 255)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 20)
    private SkillLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedencia", nullable = false, length = 20)
    private DataProvenance provenance;

    public ProfileSkillEntity() {}

    public UUID getId() { return id; }
    public ProfessionalProfileEntity getProfile() { return profile; }
    public String getSkillName() { return skillName; }
    public SkillLevel getLevel() { return level; }
    public DataProvenance getProvenance() { return provenance; }

    public void setId(UUID id) { this.id = id; }
    public void setProfile(ProfessionalProfileEntity profile) { this.profile = profile; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public void setLevel(SkillLevel level) { this.level = level; }
    public void setProvenance(DataProvenance provenance) { this.provenance = provenance; }
}

