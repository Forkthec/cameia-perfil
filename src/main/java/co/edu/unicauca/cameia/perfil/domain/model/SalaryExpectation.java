package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Expectativa salarial del candidato.
 *
 * <p>Debe ser no negativa y caber en la columna (13 dígitos enteros). Cero es válido (candidato no tiene expectativa definida o
 * prefiere no divulgarla). La moneda no se modela en Sprint 1 (COP implícita).
 */
public record SalaryExpectation(BigDecimal amount) {

    /** Dígitos enteros que caben en la columna {@code NUMERIC(15,2)}. */
    private static final int MAX_INTEGER_DIGITS = 13;

    public SalaryExpectation {
        Objects.requireNonNull(amount, "SalaryExpectation no puede ser nulo");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw InvalidFieldsException.of("amount", ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
                    "La expectativa salarial no puede ser negativa.");
        }
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw InvalidFieldsException.of("amount", ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
                    "La expectativa salarial no puede tener más de " + MAX_INTEGER_DIGITS + " dígitos.");
        }
    }
}
