package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta completar un perfil que ya está en estado COMPLETED (CM-22). */
public class ProfileAlreadyCompletedException extends BusinessException {
    public ProfileAlreadyCompletedException() {
        super(ErrorCode.PROFILE_ALREADY_COMPLETED, "El perfil ya está en estado COMPLETED");
    }
}
