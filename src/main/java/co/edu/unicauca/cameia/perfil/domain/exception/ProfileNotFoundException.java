package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.UUID;

/** Se lanza cuando no existe un perfil con el identificador solicitado. */
public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(UUID id) {
        super("No se encontró el perfil con id " + id);
    }
}
