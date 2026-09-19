package co.edu.unicauca.cameia.perfil.domain.exception;

public class ProfileAccessDeniedException extends RuntimeException {
    public ProfileAccessDeniedException() {
        super("No tienes permiso para acceder a este perfil");
    }
}
