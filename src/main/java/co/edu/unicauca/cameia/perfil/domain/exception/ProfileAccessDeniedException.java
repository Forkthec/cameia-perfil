package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil existe pero pertenece a otro Usuario. */
public class ProfileAccessDeniedException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de RT-03, el mismo del perfil inexistente. */
    public ProfileAccessDeniedException() {
        super(ErrorCode.PROFILE_NOT_ALLOWED, "No encontramos lo que buscabas.");
    }
}
