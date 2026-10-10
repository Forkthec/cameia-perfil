package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el Usuario ya tenía el máximo de perfiles de su plan antes de pedir otro. */
public class ProfileLimitReachedException extends BusinessException {

    /** Crea la excepción con el mensaje del Plan Free. */
    public ProfileLimitReachedException() {
        super(ErrorCode.PLAN_LIMIT, "Tu Plan Free permite 1 Perfil Profesional.");
    }
}
