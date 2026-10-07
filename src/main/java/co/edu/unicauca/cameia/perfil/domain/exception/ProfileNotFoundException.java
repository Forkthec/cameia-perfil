package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.UUID;

/** Se lanza cuando no existe un perfil con el identificador solicitado. */
public class ProfileNotFoundException extends BusinessException {
    public ProfileNotFoundException(UUID id) {
        super(ErrorCode.PROFILE_NOT_FOUND, "No se encontró el perfil con id " + id);
    }
}
