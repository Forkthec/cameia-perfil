package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta eliminar la única formación académica de un perfil activo. */
public class EducationNotAllowedException extends BusinessException {

    /** Crea la excepción con su código y el mensaje del criterio de aceptación. */
    public EducationNotAllowedException() {
        super(ErrorCode.EDUCATION_NOT_ALLOWED,
                "No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta.");
    }
}
