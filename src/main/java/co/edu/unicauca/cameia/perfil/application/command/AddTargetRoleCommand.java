package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Intención de agregar un rol objetivo al perfil desde el catálogo.
 *
 * <p>No lleva procedencia: un rol agregado a mano es siempre {@code MANUAL} y la fija el servicio.</p>
 *
 * @param profileId          perfil al que se agrega el rol
 * @param uid                identidad de quien llama, tomada del encabezado del Gateway
 * @param professionalRoleId rol elegido en el catálogo
 */
public record AddTargetRoleCommand(UUID profileId, String uid, UUID professionalRoleId) {

    public AddTargetRoleCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        Objects.requireNonNull(professionalRoleId, "professionalRoleId es obligatorio");
    }
}
