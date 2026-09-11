package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de cambiar el rol objetivo a otro del catálogo (CM-23). */
public record UpdateTargetRoleCommand(UUID profileId, UUID roleId, UUID professionalRoleId) {
    public UpdateTargetRoleCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        Objects.requireNonNull(roleId,    "roleId es obligatorio");
    }
}
