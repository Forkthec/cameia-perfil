package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el rol profesional pedido no existe en el catálogo. */
public class ProfessionalRoleNotFoundException extends BusinessException {

    /** Texto para la persona cuando el rol no está en el catálogo; también se usa para el rol que no se puede elegir. */
    public static final String MESSAGE = "No encontramos ese rol en el catálogo.";

    /** Crea la excepción con su código y el mensaje de CA-2.11.12. */
    public ProfessionalRoleNotFoundException() {
        super(ErrorCode.PROFESSIONAL_ROLE_NOT_FOUND, MESSAGE);
    }
}
