package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;

import java.util.Objects;

/**
 * Nombre del Perfil Profesional: de 1 a 255 caracteres tras recortar, contados en puntos de código en NFC.
 *
 * <p>El perfil nace sin nombre ({@code null} en el agregado) y el nombre se escribe al guardar la información
 * general. {@link #of(String)} aplica las reglas al texto recibido. El constructor canónico solo rechaza
 * {@code null}: reconstruye un nombre guardado sin volver a validar, para que una regla agregada después
 * nunca rompa la lectura de un perfil existente.</p>
 *
 * @param value el nombre tal como está guardado
 */
public record ProfileName(String value) {

    /** Largo máximo del nombre, igual al de la columna {@code nombre}. */
    public static final int MAX_LENGTH = 255;

    /** Rechazo de un nombre con un carácter de control (un nombre es de una sola línea). */
    public static final FieldError CHARACTERS =
            new FieldError("name", ErrorCode.PROFILE_NAME_INVALID_CHARACTERS, "El nombre tiene caracteres no permitidos.");

    /**
     * Reconstruye un nombre ya guardado.
     *
     * @param value nombre tal como está guardado; no {@code null}
     */
    public ProfileName {
        Objects.requireNonNull(value, "ProfileName no puede ser nulo — usa null directamente para ausencia");
    }

    /**
     * Crea el nombre a partir del texto recibido, aplicando todas sus reglas.
     *
     * <p>Gana la primera regla que falla: obligatorio, caracteres de control y largo, en ese orden.</p>
     *
     * @param raw nombre recibido; no {@code null}
     * @return el nombre recortado y en NFC
     * @throws InvalidFieldsException con {@code PROFILE_NAME_REQUIRED}, {@code PROFILE_NAME_INVALID_CHARACTERS}
     *                                o {@code PROFILE_NAME_TOO_LONG}
     */
    public static ProfileName of(String raw) {
        var text = SingleLineText.normalize(Objects.requireNonNull(raw, "raw"));
        if (text.isEmpty()) {
            throw InvalidFieldsException.of("name", ErrorCode.PROFILE_NAME_REQUIRED, "Ingresa un nombre para el perfil.");
        }
        if (SingleLineText.hasControlCharacter(text, false)) {
            throw InvalidFieldsException.of(CHARACTERS);
        }
        if (SingleLineText.length(text) > MAX_LENGTH) {
            throw InvalidFieldsException.of("name", ErrorCode.PROFILE_NAME_TOO_LONG,
                    "El nombre no puede superar los " + MAX_LENGTH + " caracteres.");
        }
        return new ProfileName(text);
    }
}
