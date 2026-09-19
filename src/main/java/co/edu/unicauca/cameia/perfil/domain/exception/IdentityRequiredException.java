package co.edu.unicauca.cameia.perfil.domain.exception;

public class IdentityRequiredException extends RuntimeException {
    public IdentityRequiredException() {
        super("Identidad del usuario requerida");
    }
}
