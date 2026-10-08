package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil existe pero pertenece a otro Usuario. */
public class ProfileAccessDeniedException extends BusinessException {
    /** Crea la excepción con su código y el mensaje para la persona. */
    public ProfileAccessDeniedException() {
        super(ErrorCode.PROFILE_NOT_ALLOWED, "No tienes permiso para acceder a este perfil");
    }
}
