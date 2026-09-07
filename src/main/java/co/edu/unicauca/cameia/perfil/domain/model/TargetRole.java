package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Rol profesional al que apunta el candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>El agregado limita los roles a {@code MAX_TARGET_ROLES} (backlog CM-20) y prohíbe
 * duplicados por título + seniority. La validación vive en el agregado, no aquí.
 */
public final class TargetRole {

    private static final int MAX_TITLE_LENGTH = 255;

    private final UUID id;
    private final String title;
    private final Seniority seniority;
    private final DataProvenance provenance;

    public TargetRole(UUID id, String title, Seniority seniority, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        if (title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("El título del rol debe tener entre 1 y " + MAX_TITLE_LENGTH + " caracteres");
        }
        this.title = title;
        this.seniority = Objects.requireNonNull(seniority);
        this.provenance = Objects.requireNonNull(provenance);
    }

    public boolean isSameRoleAs(TargetRole other) {
        return this.title.equalsIgnoreCase(other.title) && this.seniority == other.seniority;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public Seniority getSeniority() { return seniority; }
    public DataProvenance getProvenance() { return provenance; }
}
