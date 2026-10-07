package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando no existe un perfil con el identificador solicitado. */
public class ProfileNotFoundException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de RT-03. */
    public ProfileNotFoundException() {
        super(ErrorCode.PROFILE_NOT_FOUND, "No encontramos lo que buscabas.");
    }
}
