package co.edu.unicauca.cameia.perfil.domain.exception;

public class DuplicateSkillException extends BusinessException {
    public DuplicateSkillException(String skillName) {
        super(ErrorCode.SKILL_ALREADY_EXISTS, "La habilidad '" + skillName + "' ya está asociada al perfil");
    }
}
