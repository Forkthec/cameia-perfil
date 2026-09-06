package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de agregar una educación al perfil (CM-18). */
public record AddEducationCommand(
        UUID profileId,
        String institution,
        String degree,
        String fieldOfStudy,
        String level,
        String startDate,
        String endDate,
        boolean inProgress,
        String provenance) {

    public AddEducationCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        if (institution == null || institution.isBlank()) throw new IllegalArgumentException("institution es obligatorio");
        if (degree == null || degree.isBlank()) throw new IllegalArgumentException("degree es obligatorio");
        if (startDate == null || startDate.isBlank()) throw new IllegalArgumentException("startDate es obligatorio");
        Objects.requireNonNull(level, "level es obligatorio");
        Objects.requireNonNull(provenance, "provenance es obligatorio");
    }
}
