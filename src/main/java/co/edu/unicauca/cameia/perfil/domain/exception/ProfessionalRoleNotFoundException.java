package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando el rol profesional pedido no existe en el catálogo. */
public class ProfessionalRoleNotFoundException extends BusinessException {
    /** Crea la excepción con su código y el mensaje de CA-2.11.12. */
    public ProfessionalRoleNotFoundException() {
        super(ErrorCode.PROFESSIONAL_ROLE_NOT_FOUND, "No encontramos ese rol en el catálogo.");
    }
}
