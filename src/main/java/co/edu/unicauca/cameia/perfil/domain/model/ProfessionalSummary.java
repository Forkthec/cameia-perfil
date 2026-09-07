package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;

/**
 * Resumen profesional del candidato (campo {@code resumen}, max 2 000 caracteres per C4).
 *
 * <p>El prototipo de Frontend muestra un límite de 600 caracteres en la UI, pero el C4
 * define la columna con {@code length=2000}. El dominio respeta el contrato del dato (2 000);
 * la restricción de UI es responsabilidad del Frontend.
 */
public record ProfessionalSummary(String value) {

    private static final int MAX_LENGTH = 2000;

    public ProfessionalSummary {
        Objects.requireNonNull(value, "ProfessionalSummary no puede ser nulo");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProfessionalSummary no puede estar vacío");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "ProfessionalSummary excede el máximo de " + MAX_LENGTH + " caracteres");
        }
    }
}
