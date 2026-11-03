package co.edu.unicauca.cameia.perfil.domain.exception;

/** Otra creación de perfil del mismo Usuario no terminó a tiempo y esta se cortó sin crear nada. */
public class ProfileCreationInProgressException extends BusinessException {

    /** Crea la excepción con el mensaje que invita a reintentar. */
    public ProfileCreationInProgressException() {
        super(ErrorCode.PROFILE_CREATION_IN_PROGRESS, "Estamos creando tu perfil. Inténtalo de nuevo en unos segundos.");
    }
}
