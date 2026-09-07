package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de reemplazar título y/o seniority de un rol objetivo (HU-2.11). */
public record UpdateTargetRoleCommand(UUID profileId, UUID roleId, String title, String seniority) {
    public UpdateTargetRoleCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        Objects.requireNonNull(roleId,    "roleId es obligatorio");
    }
}
