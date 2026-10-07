package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta completar un perfil que ya está en estado COMPLETED. */
public class ProfileAlreadyCompletedException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.5.11. */
    public ProfileAlreadyCompletedException() {
        super(ErrorCode.PROFILE_ALREADY_COMPLETED, "Este perfil ya está activo.");
    }
}
