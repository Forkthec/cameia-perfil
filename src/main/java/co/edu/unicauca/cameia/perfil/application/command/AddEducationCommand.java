package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Intención de agregar una formación académica al perfil. Los textos y las fechas llegan tal cual; las reglas
 * las aplica el dominio. No lleva procedencia: lo que se agrega por esta ruta es siempre MANUAL.
 */
public record AddEducationCommand(
        UUID profileId,
        String uid,
        String institution,
        String degree,
        String level,
        String startDate,
        String endDate,
        boolean inProgress) {

    /** @throws NullPointerException si falta el perfil */
    public AddEducationCommand {
        Objects.requireNonNull(profileId, "profileId");
    }
}
