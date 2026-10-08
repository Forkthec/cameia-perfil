package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el usuario intenta crear un segundo perfil y su plan solo admite uno. */
public class ProfileAlreadyExistsException extends BusinessException {
    public ProfileAlreadyExistsException() {
        super(ErrorCode.PROFILE_LIMIT_REACHED, "El usuario ya tiene un perfil profesional creado. "
                + "El plan gratuito permite solo uno. "
                + "TODO CM-TBD: reemplazar por lógica de CuotaPlanReplica en Sprint 2.");
    }
}
