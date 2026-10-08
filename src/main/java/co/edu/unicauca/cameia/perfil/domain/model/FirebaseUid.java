package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;

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

    /**
     * Convierte la identidad que llega del Gateway o rechaza la petición si falta o no es válida.
     *
     * @param raw valor del encabezado {@code X-User-Id}
     * @return la identidad del Usuario
     * @throws IdentityRequiredException si falta, está en blanco o mide más de 128 caracteres
     */
    public static FirebaseUid required(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) throw new IdentityRequiredException();
        return new FirebaseUid(raw);
    }

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
