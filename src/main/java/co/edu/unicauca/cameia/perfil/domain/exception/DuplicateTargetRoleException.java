package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar un rol objetivo con el mismo título y seniority. */
public class DuplicateTargetRoleException extends RuntimeException {
    public DuplicateTargetRoleException(String title) {
        super("Ya existe un rol objetivo con el título '" + title + "' y ese nivel de seniority");
    }
}
