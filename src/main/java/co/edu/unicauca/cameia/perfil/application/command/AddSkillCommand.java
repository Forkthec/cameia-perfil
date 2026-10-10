package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Intención de agregar una habilidad al perfil. Los textos llegan tal cual; sus reglas las aplica el servicio, que
 * informa todos los campos rechazados a la vez.
 *
 * @param profileId  perfil al que se agrega
 * @param uid        identidad de quien lo pide
 * @param skillName  nombre recibido; {@code null} si no llegó
 * @param level      nivel recibido; {@code null} si no llegó
 */
public record AddSkillCommand(UUID profileId, String uid, String skillName, String level) {

    public AddSkillCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
    }
}
