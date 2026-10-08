package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.util.Objects;

/**
 * Reglas de texto que comparten las entidades del perfil: obligatorio y largo máximo.
 *
 * <p>El largo máximo es el de la columna, de modo que un texto que no cabe se rechaza en su campo y
 * no llega a la base de datos.</p>
 */
final class FieldRules {

    private FieldRules() { }

    /**
     * @param value           texto recibido
     * @param field           nombre del campo en el cuerpo de la petición
     * @param requiredCode    código si el texto está en blanco
     * @param requiredMessage mensaje si el texto está en blanco
     * @param tooLongCode     código si el texto supera el máximo
     * @param label           nombre del campo con su artículo, como «La empresa»
     * @param max             largo máximo
     * @return el mismo texto
     * @throws InvalidFieldsException si está en blanco o supera el máximo
     */
    static String requiredText(String value, String field, ErrorCode requiredCode, String requiredMessage,
                               ErrorCode tooLongCode, String label, int max) {
        Objects.requireNonNull(value, field + " no puede ser nulo");
        if (value.isBlank()) {
            throw InvalidFieldsException.of(field, requiredCode, requiredMessage);
        }
        return optionalText(value, field, tooLongCode, label, max);
    }

    /**
     * @param value       texto recibido, o {@code null}
     * @param field       nombre del campo en el cuerpo de la petición
     * @param tooLongCode código si el texto supera el máximo
     * @param label       nombre del campo con su artículo, como «La descripción»
     * @param max         largo máximo
     * @return el mismo texto, o {@code null} si no llegó
     * @throws InvalidFieldsException si supera el máximo
     */
    static String optionalText(String value, String field, ErrorCode tooLongCode, String label, int max) {
        if (value != null && value.length() > max) {
            throw InvalidFieldsException.of(field, tooLongCode, label + " no puede superar los " + max + " caracteres.");
        }
        return value;
    }
}
