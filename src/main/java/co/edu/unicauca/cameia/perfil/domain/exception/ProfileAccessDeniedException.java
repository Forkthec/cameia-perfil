package co.edu.unicauca.cameia.perfil.domain.exception;

public class ProfileAccessDeniedException extends BusinessException {
    public ProfileAccessDeniedException() {
        super(ErrorCode.PROFILE_NOT_ALLOWED, "No tienes permiso para acceder a este perfil");
    }
}
