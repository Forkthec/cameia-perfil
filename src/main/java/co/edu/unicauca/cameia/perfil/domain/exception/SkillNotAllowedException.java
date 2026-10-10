package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta eliminar la única habilidad de un perfil activo. */
public class SkillNotAllowedException extends BusinessException {

    /** Crea la excepción con su código y el mensaje del criterio de aceptación. */
    public SkillNotAllowedException() {
        super(ErrorCode.SKILL_NOT_ALLOWED,
                "No puedes quedarte sin habilidades con el perfil activo. Agrega otra antes de eliminar esta.");
    }
}
