package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar un rol objetivo superando el límite del perfil. */
public class MaxTargetRolesExceededException extends BusinessException {
    /** @param max cantidad máxima de roles objetivo por perfil; el mensaje es el de CA-2.11.5 */
    public MaxTargetRolesExceededException(int max) {
        super(ErrorCode.TARGET_ROLE_LIMIT_REACHED, "Ya tienes el máximo de " + max + " roles objetivo.");
    }
}
