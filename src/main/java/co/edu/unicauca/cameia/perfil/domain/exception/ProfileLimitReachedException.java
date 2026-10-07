package co.edu.unicauca.cameia.perfil.domain.exception;

/** El Usuario alcanzó el máximo de Perfiles Profesionales de su plan. */
public class ProfileLimitReachedException extends BusinessException {

    /** Crea la excepción con el mensaje del Plan Free. */
    public ProfileLimitReachedException() {
        super(ErrorCode.PROFILE_LIMIT_REACHED, "Tu Plan Free permite 1 Perfil Profesional.");
    }
}
