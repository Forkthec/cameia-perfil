package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar un rol objetivo superando el límite del perfil. */
public class MaxTargetRolesExceededException extends RuntimeException {
    public MaxTargetRolesExceededException(int max) {
        super("El perfil ya tiene el máximo de " + max + " roles objetivo permitidos");
    }
}
