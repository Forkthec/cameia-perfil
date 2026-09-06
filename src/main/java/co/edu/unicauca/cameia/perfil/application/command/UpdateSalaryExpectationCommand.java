package co.edu.unicauca.cameia.perfil.application.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Intención de actualizar la expectativa salarial del perfil (CM-19). */
public record UpdateSalaryExpectationCommand(UUID profileId, BigDecimal amount) {

    public UpdateSalaryExpectationCommand {
        Objects.requireNonNull(profileId, "profileId es obligatorio");
        Objects.requireNonNull(amount, "amount es obligatorio");
    }
}
