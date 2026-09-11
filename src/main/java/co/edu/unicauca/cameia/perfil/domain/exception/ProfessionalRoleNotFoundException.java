package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.UUID;

public class ProfessionalRoleNotFoundException extends RuntimeException {
    public ProfessionalRoleNotFoundException(UUID roleId) {
        super("Rol profesional no encontrado: " + roleId);
    }
}
