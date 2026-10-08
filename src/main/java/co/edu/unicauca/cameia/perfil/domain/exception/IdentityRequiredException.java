package co.edu.unicauca.cameia.perfil.domain.exception;

public class IdentityRequiredException extends BusinessException {
    public IdentityRequiredException() {
        super(ErrorCode.IDENTITY_REQUIRED, "Identidad del usuario requerida");
    }
}
