package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;

/**
 * UID del usuario en Firebase Authentication, propagado por el Gateway en el header X-User-Id.
 *
 * <p>Firebase UIDs tienen hasta 128 caracteres alfanuméricos. Validamos el contrato aquí
 * para que ninguna capa externa pase un string vacío o demasiado largo sin que el dominio se entere.
 */
public record FirebaseUid(String value) {

    /** Longitud máxima de un UID de Firebase. */
    public static final int MAX_LENGTH = 128;

    public FirebaseUid {
        Objects.requireNonNull(value, "FirebaseUid no puede ser nulo");
        if (value.isBlank()) {
            throw new IllegalArgumentException("FirebaseUid no puede estar vacío");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "FirebaseUid excede el máximo de " + MAX_LENGTH + " caracteres");
        }
    }
}
