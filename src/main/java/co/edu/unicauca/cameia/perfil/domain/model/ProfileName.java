package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.util.Objects;

/**
 * Nombre del perfil profesional (campo {@code nombre} en la entidad JPA).
 *
 * <p>Es opcional al crear el perfil (POST /profiles lo puede omitir), pero si se provee
 * no puede estar vacío ni superar 255 caracteres. Crear {@code ProfileName(null)} no es válido;
 * la ausencia del nombre se representa con {@code null} directamente en el agregado.</p>
 * <p>El backlog lo resuelve así: el perfil nace sin nombre y el nombre es obligatorio al guardar
 * la información general (1 a 255 caracteres).</p>
 */
public record ProfileName(String value) {

    private static final int MAX_LENGTH = 255;

    public ProfileName {
        Objects.requireNonNull(value, "ProfileName no puede ser nulo — usa null directamente para ausencia");
        if (value.isBlank()) {
            throw InvalidFieldsException.of("name", ErrorCode.PROFILE_NAME_REQUIRED, "Ingresa un nombre para el perfil.");
        }
        if (value.length() > MAX_LENGTH) {
            throw InvalidFieldsException.of("name", ErrorCode.PROFILE_NAME_TOO_LONG,
                    "El nombre no puede superar los " + MAX_LENGTH + " caracteres.");
        }
    }
}
