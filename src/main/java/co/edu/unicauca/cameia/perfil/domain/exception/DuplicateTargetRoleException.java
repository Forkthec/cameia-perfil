package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar un rol objetivo que ya existe en el perfil (mismo professionalRoleId). */
public class DuplicateTargetRoleException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.11.4 y CA-2.11.8. */
    public DuplicateTargetRoleException() {
        super(ErrorCode.TARGET_ROLE_ALREADY_EXISTS, "Ese rol objetivo ya está en tu perfil.");
    }
}
