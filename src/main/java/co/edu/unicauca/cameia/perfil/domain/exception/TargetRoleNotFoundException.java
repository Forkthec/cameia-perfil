package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil no tiene un rol objetivo con el identificador solicitado. */
public class TargetRoleNotFoundException extends BusinessException {
    /** Crea la excepción con su código y el mensaje para la persona. */
    public TargetRoleNotFoundException() {
        super(ErrorCode.TARGET_ROLE_NOT_FOUND, "No encontramos lo que buscabas.");
    }
}
