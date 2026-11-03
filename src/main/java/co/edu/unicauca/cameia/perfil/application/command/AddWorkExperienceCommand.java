package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Intención de agregar una experiencia laboral al perfil. Los textos y las fechas llegan tal cual; las reglas
 * las aplica el dominio. No lleva procedencia: lo que se agrega por esta ruta es siempre MANUAL.
 */
public record AddWorkExperienceCommand(
        UUID profileId,
        String uid,
        String company,
        String position,
        String description,
        String startDate,
        String endDate,
        String employmentStatus) {

    /** @throws NullPointerException si falta el perfil */
    public AddWorkExperienceCommand {
        Objects.requireNonNull(profileId, "profileId");
    }
}
