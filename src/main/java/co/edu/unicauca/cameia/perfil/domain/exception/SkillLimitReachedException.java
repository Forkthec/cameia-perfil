package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil ya tiene el máximo de habilidades y se intenta agregar otra. */
public class SkillLimitReachedException extends BusinessException {

    /** Crea la excepción con su código y el mensaje del criterio de aceptación. */
    public SkillLimitReachedException() {
        super(ErrorCode.SKILL_LIMIT_REACHED, "Ya tienes el máximo de 20 habilidades.");
    }
}
