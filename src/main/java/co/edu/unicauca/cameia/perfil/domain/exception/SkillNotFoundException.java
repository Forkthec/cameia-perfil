package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil no tiene la habilidad con el identificador solicitado (ajena o inexistente). */
public class SkillNotFoundException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.5.23. */
    public SkillNotFoundException() {
        super(ErrorCode.SKILL_NOT_FOUND, "No encontramos lo que buscabas.");
    }
}
