package co.edu.unicauca.cameia.perfil.application.command;

import java.util.Objects;
import java.util.UUID;

/** Intención de agregar una habilidad al perfil (CM-19). */
public record AddSkillCommand(UUID profileId, String skillName, String level, String provenance) {

    public AddSkillCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        if (skillName == null || skillName.isBlank()) throw new IllegalArgumentException("skillName es obligatorio");
        Objects.requireNonNull(level, "level es obligatorio");
        Objects.requireNonNull(provenance, "provenance es obligatorio");
    }
}
