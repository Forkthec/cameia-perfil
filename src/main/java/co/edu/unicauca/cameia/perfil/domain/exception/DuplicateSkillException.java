package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta agregar al perfil una habilidad que ya tiene. */
public class DuplicateSkillException extends BusinessException {
    /** @param skillName nombre de la habilidad repetida */
    public DuplicateSkillException(String skillName) {
        super(ErrorCode.SKILL_ALREADY_EXISTS, "La habilidad '" + skillName + "' ya está asociada al perfil");
    }
}
