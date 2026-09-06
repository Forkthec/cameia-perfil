package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de agregar una experiencia laboral al perfil (CM-18). */
public record AddWorkExperienceCommand(
        UUID profileId,
        String company,
        String position,
        String description,
        String startDate,
        String endDate,
        String employmentStatus,
        String seniority,
        String provenance) {

    public AddWorkExperienceCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        if (company == null || company.isBlank()) throw new IllegalArgumentException("company es obligatorio");
        if (position == null || position.isBlank()) throw new IllegalArgumentException("position es obligatorio");
        if (startDate == null || startDate.isBlank()) throw new IllegalArgumentException("startDate es obligatorio");
        Objects.requireNonNull(employmentStatus, "employmentStatus es obligatorio");
        Objects.requireNonNull(seniority, "seniority es obligatorio");
        Objects.requireNonNull(provenance, "provenance es obligatorio");
    }
}
