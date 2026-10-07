package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar un rol objetivo que ya existe en el perfil (mismo professionalRoleId). */
public class DuplicateTargetRoleException extends BusinessException {
    /** @param roleTitle nombre del rol objetivo repetido */
    public DuplicateTargetRoleException(String roleTitle) {
        super(ErrorCode.TARGET_ROLE_ALREADY_EXISTS, "Ya existe un rol objetivo con el rol '" + roleTitle + "' en este perfil");
    }
}
