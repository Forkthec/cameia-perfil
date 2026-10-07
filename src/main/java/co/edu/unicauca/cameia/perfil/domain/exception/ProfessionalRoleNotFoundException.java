package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.UUID;

/** Se lanza cuando el rol profesional pedido no existe en el catálogo. */
public class ProfessionalRoleNotFoundException extends BusinessException {
    /** @param roleId identificador del rol profesional buscado */
    public ProfessionalRoleNotFoundException(UUID roleId) {
        super(ErrorCode.PROFESSIONAL_ROLE_NOT_FOUND, "Rol profesional no encontrado: " + roleId);
    }
}
