package co.edu.unicauca.cameia.perfil.domain.exception;

/**
 * Evento de cuenta cuyo contenido incumple el contrato publicado. Nunca llega a HTTP: el consumidor rechaza el mensaje hacia la
 * cola de fallidos. La razón es un código estable para el log; el mensaje nunca incluye datos del evento.
 */
public class InvalidAccountEventException extends RuntimeException {

    /** Razones estables, que se registran junto con el identificador del mensaje. */
    public enum Reason {
        MESSAGE_ID_INVALID, USER_ID_INVALID, BIRTH_DATE_REQUIRED, BIRTH_DATE_IN_THE_FUTURE, BIRTH_DATE_OUT_OF_RANGE
    }

    private final Reason reason;

    /**
     * Crea la excepción con la razón del rechazo.
     *
     * @param reason por qué se rechazó el evento
     */
    public InvalidAccountEventException(Reason reason) {
        super("Evento de cuenta inválido: " + reason);
        this.reason = reason;
    }

    /** @return por qué se rechazó el evento */
    public Reason getReason() {
        return reason;
    }
}
