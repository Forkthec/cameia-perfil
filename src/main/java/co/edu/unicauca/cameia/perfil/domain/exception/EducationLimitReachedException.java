package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil ya tiene el máximo de formaciones académicas y se intenta agregar otra. */
public class EducationLimitReachedException extends BusinessException {

    /** Crea la excepción con su código y el mensaje del criterio de aceptación. */
    public EducationLimitReachedException() {
        super(ErrorCode.EDUCATION_LIMIT_REACHED, "Ya tienes el máximo de 5 formaciones académicas.");
    }
}
