package co.edu.unicauca.cameia.perfil.domain.exception;

/**
 * Raíz de las excepciones de negocio de Perfil.
 *
 * <p>Cada una lleva un código estable que el cliente puede usar para decidir qué mostrar.
 * El estado HTTP lo decide la capa de presentación a partir del código, para que el dominio
 * no dependa de la web.</p>
 */
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode code;

    /**
     * @param code código estable del error
     * @param message mensaje para la persona, en español
     */
    protected BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** @return código estable del error */
    public ErrorCode getCode() {
        return code;
    }
}
