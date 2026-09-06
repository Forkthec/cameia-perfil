package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de agregar un rol objetivo al perfil (CM-20). */
public record AddTargetRoleCommand(UUID profileId, String title, String seniority, String provenance) {

    public AddTargetRoleCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title es obligatorio");
        Objects.requireNonNull(seniority, "seniority es obligatorio");
        Objects.requireNonNull(provenance, "provenance es obligatorio");
    }
}
