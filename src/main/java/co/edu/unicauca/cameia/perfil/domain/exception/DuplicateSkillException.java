package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar al perfil una habilidad que ya tiene. */
public class DuplicateSkillException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.5.2. */
    public DuplicateSkillException() {
        super(ErrorCode.SKILL_ALREADY_EXISTS, "Esa habilidad ya está en tu perfil.");
    }
}
