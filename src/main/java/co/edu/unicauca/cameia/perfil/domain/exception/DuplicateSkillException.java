package co.edu.unicauca.cameia.perfil.domain.exception;

public class DuplicateSkillException extends RuntimeException {
    public DuplicateSkillException(String skillName) {
        super("La habilidad '" + skillName + "' ya está asociada al perfil");
    }
}
