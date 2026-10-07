package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta eliminar el único rol objetivo que le queda al perfil. */
public class LastTargetRoleException extends BusinessException {
    public LastTargetRoleException() {
        super(ErrorCode.TARGET_ROLE_NOT_ALLOWED, "El perfil debe tener al menos un rol objetivo; no se puede eliminar el último");
    }
}
