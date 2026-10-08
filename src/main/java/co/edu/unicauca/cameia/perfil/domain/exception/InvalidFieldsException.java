package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.List;
import java.util.Objects;

/**
 * Se lanza cuando uno o más datos del perfil no cumplen una regla del dominio: un largo, una fecha,
 * una opción que no existe o una combinación de campos que no es posible.
 *
 * <p>Responde como la validación de Bean Validation: {@code VALIDATION_FAILED} con un elemento por
 * campo, cada uno con su propio código y un mensaje que dice qué corregir.</p>
 */
public class InvalidFieldsException extends BusinessException {

    /**
     * Un campo rechazado.
     *
     * @param field   nombre del campo en el cuerpo de la petición
     * @param code    código estable de la regla incumplida
     * @param message mensaje para la persona, en español, que dice qué corregir
     */
    public record FieldError(String field, ErrorCode code, String message) {
        public FieldError {
            Objects.requireNonNull(field);
            Objects.requireNonNull(code);
            Objects.requireNonNull(message);
        }
    }

    private final List<FieldError> errors;

    /** @param errors campos rechazados, al menos uno */
    public InvalidFieldsException(List<FieldError> errors) {
        super(ErrorCode.VALIDATION_FAILED, "Revisa los campos marcados.");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("InvalidFieldsException necesita al menos un campo rechazado");
        }
        this.errors = List.copyOf(errors);
    }

    /**
     * Rechaza un solo campo.
     *
     * @param field   nombre del campo en el cuerpo de la petición
     * @param code    código estable de la regla incumplida
     * @param message mensaje para la persona
     * @return la excepción lista para lanzar
     */
    public static InvalidFieldsException of(String field, ErrorCode code, String message) {
        return new InvalidFieldsException(List.of(new FieldError(field, code, message)));
    }

    /** @return campos rechazados, en el orden en que se detectaron */
    public List<FieldError> getErrors() {
        return errors;
    }
}
