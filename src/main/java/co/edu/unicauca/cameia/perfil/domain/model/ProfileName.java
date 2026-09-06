package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;

/**
 * Nombre del perfil profesional (campo {@code nombre} en la entidad JPA).
 *
 * <p>Es opcional al crear el perfil (POST /profiles lo puede omitir), pero si se provee
 * no puede estar vacío ni superar 255 caracteres. Crear {@code ProfileName(null)} no es válido;
 * la ausencia del nombre se representa con {@code null} directamente en el agregado.
 * TODO CM-TBD-PO: confirmar si nombre_perfil es obligatorio al crear (prototipo lo muestra primero).
 */
public record ProfileName(String value) {

    private static final int MAX_LENGTH = 255;

    public ProfileName {
        Objects.requireNonNull(value, "ProfileName no puede ser nulo — usa null directamente para ausencia");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProfileName no puede estar vacío");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "ProfileName excede el máximo de " + MAX_LENGTH + " caracteres");
        }
    }
}
