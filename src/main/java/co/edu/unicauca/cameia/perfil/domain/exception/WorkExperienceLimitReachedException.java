package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil ya tiene el máximo de experiencias laborales y se intenta agregar otra. */
public class WorkExperienceLimitReachedException extends BusinessException {

    /** Crea la excepción con su código y el mensaje del criterio de aceptación. */
    public WorkExperienceLimitReachedException() {
        super(ErrorCode.WORK_EXPERIENCE_LIMIT_REACHED, "Ya tienes el máximo de 4 experiencias laborales.");
    }
}
