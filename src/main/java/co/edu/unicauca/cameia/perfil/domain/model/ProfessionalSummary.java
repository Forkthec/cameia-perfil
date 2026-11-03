package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;

import java.util.Objects;

/**
 * Resumen profesional del candidato: de 1 a 2000 caracteres tras recortar, contados en puntos de código en NFC.
 *
 * <p>El resumen puede tener varias líneas: se conservan los saltos internos y se recortan solo los extremos.
 * La columna admite 2000 caracteres; el límite que muestre la interfaz es asunto de Frontend. {@link #of(String)}
 * aplica las reglas al texto recibido. El constructor canónico solo rechaza {@code null}: reconstruye un resumen
 * guardado sin volver a validar, para que una regla agregada después nunca rompa la lectura de un perfil existente.</p>
 *
 * @param value el resumen tal como está guardado
 */
public record ProfessionalSummary(String value) {

    /** Largo máximo del resumen, igual al de la columna {@code resumen}. */
    public static final int MAX_LENGTH = 2000;

    /** Rechazo de un resumen con un carácter de control distinto de tabulador, salto de línea y retorno. */
    public static final FieldError CHARACTERS =
            new FieldError("summary", ErrorCode.SUMMARY_INVALID_CHARACTERS, "El resumen tiene caracteres no permitidos.");

    /**
     * Reconstruye un resumen ya guardado.
     *
     * @param value resumen tal como está guardado; no {@code null}
     */
    public ProfessionalSummary {
        Objects.requireNonNull(value, "ProfessionalSummary no puede ser nulo");
    }

    /**
     * Crea el resumen a partir del texto recibido, aplicando todas sus reglas.
     *
     * <p>Gana la primera regla que falla: caracteres de control y largo, en ese orden.</p>
     *
     * @param raw resumen recibido; no {@code null} ni vacío tras recortar
     * @return el resumen recortado y en NFC
     * @throws InvalidFieldsException   con {@code SUMMARY_INVALID_CHARACTERS} o {@code SUMMARY_TOO_LONG}
     * @throws IllegalArgumentException si el texto queda vacío: borrar el resumen es asunto del servicio, que lo
     *                                  convierte en {@code null} antes de llamar
     */
    public static ProfessionalSummary of(String raw) {
        var text = SingleLineText.normalize(Objects.requireNonNull(raw, "raw"));
        if (text.isEmpty()) {
            throw new IllegalArgumentException("ProfessionalSummary no puede estar en blanco: para borrarlo se usa null");
        }
        if (SingleLineText.hasControlCharacter(text, true)) {
            throw InvalidFieldsException.of(CHARACTERS);
        }
        if (SingleLineText.length(text) > MAX_LENGTH) {
            throw InvalidFieldsException.of("summary", ErrorCode.SUMMARY_TOO_LONG,
                    "El resumen no puede superar los " + MAX_LENGTH + " caracteres.");
        }
        return new ProfessionalSummary(text);
    }
}
