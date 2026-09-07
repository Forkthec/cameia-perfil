package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Habilidad asociada al perfil (C4: HabilidadPerfil). Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>El catálogo de habilidades ({@code Habilidad} en C4) no se modela en el dominio de Perfil:
 * la clave es el nombre de la habilidad como string, que el Frontend valida contra el catálogo.
 * Esto evita acoplar el dominio de Perfil al microservicio que owna el catálogo.
 */
public final class ProfileSkill {

    private static final int MAX_NAME_LENGTH = 255;

    private final UUID id;
    private final String skillName;
    private final SkillLevel level;
    private final DataProvenance provenance;

    public ProfileSkill(UUID id, String skillName, SkillLevel level, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        Objects.requireNonNull(skillName);
        if (skillName.isBlank() || skillName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("skillName debe tener entre 1 y " + MAX_NAME_LENGTH + " caracteres");
        }
        this.skillName = skillName;
        this.level = Objects.requireNonNull(level);
        this.provenance = Objects.requireNonNull(provenance);
    }

    public UUID getId() { return id; }
    public String getSkillName() { return skillName; }
    public SkillLevel getLevel() { return level; }
    public DataProvenance getProvenance() { return provenance; }
}
