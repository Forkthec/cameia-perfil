package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de agregar un rol objetivo al perfil desde el catálogo (CM-23). */
public record AddTargetRoleCommand(UUID profileId, UUID professionalRoleId, String provenance) {

    public AddTargetRoleCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        Objects.requireNonNull(professionalRoleId, "professionalRoleId es obligatorio");
        Objects.requireNonNull(provenance, "provenance es obligatorio");
    }
}
