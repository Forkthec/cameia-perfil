package co.edu.unicauca.cameia.perfil.domain.exception;

/** Otra creación de perfil del mismo Usuario no terminó a tiempo y esta se cortó sin crear nada. */
public class ProfileCreationTimeoutException extends BusinessException {

    /** Crea la excepción con el mensaje que invita a reintentar. */
    public ProfileCreationTimeoutException() {
        super(ErrorCode.PROFILE_CREATION_TIMEOUT, "Estamos creando tu perfil. Inténtalo de nuevo en unos segundos.");
    }
}
