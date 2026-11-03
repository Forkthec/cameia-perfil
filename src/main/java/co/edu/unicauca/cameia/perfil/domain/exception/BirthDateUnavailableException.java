package co.edu.unicauca.cameia.perfil.domain.exception;

/**
 * La fecha de nacimiento del Usuario aún no se replicó, así que no se puede validar una experiencia laboral o una formación con fechas.
 * Se responde como un fallo temporal que la persona puede reintentar; no se guarda nada.
 */
public class BirthDateUnavailableException extends BusinessException {

    /** Crea la excepción con el mensaje de reintento para la persona. */
    public BirthDateUnavailableException() {
        super(ErrorCode.BIRTH_DATE_UNAVAILABLE, "Ocurrió un error. Inténtalo de nuevo.");
    }
}
