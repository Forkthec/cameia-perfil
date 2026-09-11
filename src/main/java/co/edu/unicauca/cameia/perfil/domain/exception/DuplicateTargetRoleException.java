package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar un rol objetivo que ya existe en el perfil (mismo professionalRoleId). */
public class DuplicateTargetRoleException extends RuntimeException {
    public DuplicateTargetRoleException(String roleTitle) {
        super("Ya existe un rol objetivo con el rol '" + roleTitle + "' en este perfil");
    }
}
