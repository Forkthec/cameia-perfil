package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando la petición no trae la identidad del Usuario o la trae en blanco o demasiado larga. */
public class IdentityRequiredException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de RT-02-CA02. */
    public IdentityRequiredException() {
        super(ErrorCode.IDENTITY_REQUIRED, "Tu sesión expiró. Inicia sesión de nuevo.");
    }
}
