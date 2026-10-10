package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Intención de actualizar la información general de un perfil.
 *
 * <p>Todos los campos excepto {@code profileId} y {@code uid} son opcionales: {@code null} significa «no cambiar».
 * Un resumen en blanco significa «borrarlo». No lleva procedencia: la del resumen se deriva.</p>
 *
 * @param profileId         identificador del perfil
 * @param uid               identidad del Usuario que edita, tomada del encabezado del Gateway
 * @param name              nombre nuevo, o {@code null} para no cambiarlo
 * @param summary           resumen nuevo, {@code ""} para borrarlo o {@code null} para no cambiarlo
 * @param preferredModality modalidad preferida nueva, o {@code null} para no cambiarla
 */
public record UpdateProfileInfoCommand(
        UUID profileId,
        String uid,
        String name,
        String summary,
        String preferredModality) {

    public UpdateProfileInfoCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
    }
}
