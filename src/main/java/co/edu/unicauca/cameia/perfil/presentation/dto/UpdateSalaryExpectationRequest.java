package co.edu.unicauca.cameia.perfil.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Body del PATCH /api/v1/profiles/{id}/salary-expectation (CM-19). */
public record UpdateSalaryExpectationRequest(@NotNull BigDecimal amount) {
}
