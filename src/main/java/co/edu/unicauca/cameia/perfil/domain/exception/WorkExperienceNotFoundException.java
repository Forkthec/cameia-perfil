package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el perfil no tiene la experiencia laboral con el identificador solicitado (ajena o inexistente). */
public class WorkExperienceNotFoundException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.4.59. */
    public WorkExperienceNotFoundException() {
        super(ErrorCode.WORK_EXPERIENCE_NOT_FOUND, "No encontramos lo que buscabas.");
    }
}
