package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Habilidad asociada al perfil (Habilidad del perfil). Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>El catálogo de habilidades no se modela en el dominio de Perfil: la habilidad es el nombre que escribe la persona,
 * con las reglas de {@link SkillName}. Esto evita acoplar el dominio de Perfil al servicio que sea dueño de un catálogo.</p>
 */
public final class ProfileSkill {

    private final UUID id;
    private final SkillName name;
    private final SkillLevel level;
    private final DataProvenance provenance;

    /**
     * Crea la habilidad.
     *
     * @param id         identificador de la habilidad
     * @param name       nombre ya validado
     * @param level      nivel
     * @param provenance procedencia del dato
     */
    public ProfileSkill(UUID id, SkillName name, SkillLevel level, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.level = Objects.requireNonNull(level);
        this.provenance = Objects.requireNonNull(provenance);
    }

    /** @return identificador de la habilidad */
    public UUID getId() { return id; }

    /** @return nombre de la habilidad con sus reglas */
    public SkillName getName() { return name; }

    /** @return el texto del nombre tal como se guarda */
    public String getSkillName() { return name.value(); }

    /** @return nivel */
    public SkillLevel getLevel() { return level; }

    /** @return procedencia del dato */
    public DataProvenance getProvenance() { return provenance; }
}
