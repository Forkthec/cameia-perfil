package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Rechaza un solo campo a partir de un error ya armado.
     *
     * @param error campo rechazado, con su código y su mensaje
     * @return la excepción lista para lanzar
     */
    public static InvalidFieldsException of(FieldError error) {
        return new InvalidFieldsException(List.of(error));
    }

    /**
     * Junta los errores de campo mientras corren todas las reglas de un registro y conserva solo el
     * primero de cada campo, para que una sola respuesta liste todos los campos rechazados a la vez.
     */
    public static final class Collector {

        private final Map<String, FieldError> firstPerField = new LinkedHashMap<>();

        /**
         * Anota un error salvo que el campo ya tenga uno.
         *
         * @param field   nombre del campo en el JSON
         * @param code    código estable del error
         * @param message mensaje para la persona
         */
        public void add(String field, ErrorCode code, String message) {
            add(new FieldError(field, code, message));
        }

        /**
         * Anota un error ya armado salvo que su campo ya tenga uno.
         *
         * @param error campo rechazado, con su código y su mensaje
         */
        public void add(FieldError error) {
            firstPerField.putIfAbsent(error.field(), error);
        }

        /**
         * Indica si un campo ya tiene un error anotado.
         *
         * @param field nombre del campo en el JSON
         * @return {@code true} si el campo ya tiene un error
         */
        public boolean hasError(String field) {
            return firstPerField.containsKey(field);
        }

        /**
         * Lanza la excepción con todos los errores anotados.
         *
         * @throws InvalidFieldsException si hay al menos un error anotado
         */
        public void throwIfAny() {
            if (!firstPerField.isEmpty()) {
                throw new InvalidFieldsException(List.copyOf(firstPerField.values()));
            }
        }
    }

    /** @return campos rechazados, en el orden en que se detectaron */
    public List<FieldError> getErrors() {
        return errors;
    }
}
