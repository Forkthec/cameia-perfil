package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Rol profesional al que apunta el candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>El agregado limita los roles a {@code MAX_TARGET_ROLES} y prohíbe duplicados por título.
 * Desde CM-23 los duplicados se detectan por {@code professionalRoleId}.
 */
public final class TargetRole {

    private static final int MAX_TITLE_LENGTH = 255;

    private final UUID id;
    private final UUID professionalRoleId;
    private final String roleTitle;
    private final DataProvenance provenance;

    public TargetRole(UUID id, UUID professionalRoleId, String roleTitle, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.professionalRoleId = Objects.requireNonNull(professionalRoleId, "professionalRoleId es obligatorio");
        Objects.requireNonNull(roleTitle, "roleTitle es obligatorio");
        if (roleTitle.isBlank() || roleTitle.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("El título del rol debe tener entre 1 y " + MAX_TITLE_LENGTH + " caracteres");
        }
        this.roleTitle = roleTitle;
        this.provenance = Objects.requireNonNull(provenance);
    }

    public boolean isSameRoleAs(TargetRole other) {
        return this.professionalRoleId.equals(other.professionalRoleId);
    }

    public UUID getId() { return id; }
    public UUID getProfessionalRoleId() { return professionalRoleId; }
    public String getRoleTitle() { return roleTitle; }
    public DataProvenance getProvenance() { return provenance; }
}
