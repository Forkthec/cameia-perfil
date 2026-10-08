package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta eliminar el único rol objetivo que le queda al perfil. */
public class LastTargetRoleException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.11.3. */
    public LastTargetRoleException() {
        super(ErrorCode.TARGET_ROLE_NOT_ALLOWED, "No puedes quedarte sin roles objetivo con el perfil activo.");
    }
}
