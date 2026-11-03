package co.edu.unicauca.cameia.perfil.domain.exception;

/** Otra escritura retuvo el perfil más que la espera máxima y esta se cortó sin cambiar nada. */
public class ProfileUpdateInProgressException extends BusinessException {

    /** Crea la excepción con el mensaje que invita a reintentar. */
    public ProfileUpdateInProgressException() {
        super(ErrorCode.PROFILE_UPDATE_IN_PROGRESS,
                "Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.");
    }
}
