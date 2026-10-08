package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.UUID;

public class ProfessionalRoleNotFoundException extends BusinessException {
    public ProfessionalRoleNotFoundException(UUID roleId) {
        super(ErrorCode.PROFESSIONAL_ROLE_NOT_FOUND, "Rol profesional no encontrado: " + roleId);
    }
}
