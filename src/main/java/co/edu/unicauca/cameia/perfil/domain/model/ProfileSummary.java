package co.edu.unicauca.cameia.perfil.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista mínima de un Perfil Profesional para la lista: lo que la pantalla necesita y nada más.
 *
 * @param id          identificador del perfil
 * @param name        nombre del perfil; {@code null} si aún no tiene
 * @param status      estado del ciclo de vida
 * @param createdAt   instante de creación, que fija el orden de la lista
 * @param targetRoles roles objetivo en el orden en que se agregaron
 */
public record ProfileSummary(ProfileId id, String name, ProfileStatus status, Instant createdAt,
                             List<TargetRoleSummary> targetRoles) {

    /**
     * Rol objetivo tal como se muestra en la lista.
     *
     * @param professionalRoleId identificador del rol en el catálogo
     * @param roleTitle          nombre del rol guardado en el perfil
     */
    public record TargetRoleSummary(UUID professionalRoleId, String roleTitle) { }

    public ProfileSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        targetRoles = List.copyOf(Objects.requireNonNull(targetRoles, "targetRoles"));
    }
}
