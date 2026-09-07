package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Intención de actualizar la información general de un perfil (CM-17).
 * Todos los campos excepto {@code profileId} son opcionales: {@code null} significa "no cambiar".
 */
public record UpdateProfileInfoCommand(
        UUID profileId,
        String name,
        String summary,
        String preferredModality,
        String provenance) {

    public UpdateProfileInfoCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
    }
}
