package co.edu.unicauca.cameia.perfil.domain.exception;

/**
 * Se lanza cuando el filtro de estado de la lista de perfiles no es un estado del perfil.
 * El mensaje no repite el valor recibido.
 */
public class ProfileStatusInvalidException extends BusinessException {

    /** Crea la excepción con su código y el mensaje para la persona. */
    public ProfileStatusInvalidException() {
        super(ErrorCode.PROFILE_STATUS_INVALID_VALUE, "Selecciona una opción.");
    }
}
