package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Expectativa salarial del candidato.
 *
 * <p>Debe ser no negativa. Cero es válido (candidato no tiene expectativa definida o
 * prefiere no divulgarla). La moneda no se modela en Sprint 1 (COP implícita).
 */
public record SalaryExpectation(BigDecimal amount) {

    public SalaryExpectation {
        Objects.requireNonNull(amount, "SalaryExpectation no puede ser nulo");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw InvalidFieldsException.of("amount", ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
                    "La expectativa salarial no puede ser negativa.");
        }
    }
}
