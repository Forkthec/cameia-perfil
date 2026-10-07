package co.edu.unicauca.cameia.perfil.domain.exception;

/**
 * Se lanza cuando se pide el catálogo de roles profesionales en un idioma que no está disponible.
 * El mensaje no repite el valor recibido.
 */
public class UnsupportedLanguageException extends BusinessException {

    /** Crea la excepción con su código y el mensaje para la persona. */
    public UnsupportedLanguageException() {
        super(ErrorCode.PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE, "Elige un idioma disponible: español o inglés.");
    }
}
