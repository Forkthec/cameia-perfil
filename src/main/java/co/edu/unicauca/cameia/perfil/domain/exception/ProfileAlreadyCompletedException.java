package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta completar un perfil que ya está en estado COMPLETED. */
public class ProfileAlreadyCompletedException extends BusinessException {
    /** Crea la excepción con su código y el mensaje para la persona. */
    public ProfileAlreadyCompletedException() {
        super(ErrorCode.PROFILE_ALREADY_COMPLETED, "El perfil ya está en estado COMPLETED");
    }
}
