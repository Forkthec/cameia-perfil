package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil no tiene la formación académica con el identificador solicitado (ajena o inexistente). */
public class EducationNotFoundException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.4.58. */
    public EducationNotFoundException() {
        super(ErrorCode.EDUCATION_NOT_FOUND, "No encontramos lo que buscabas.");
    }
}
