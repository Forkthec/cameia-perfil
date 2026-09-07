package co.edu.unicauca.cameia.perfil.domain.exception;

/** Se lanza cuando se intenta enviar a revisión un perfil que no cumple los campos mínimos. */
public class IncompleteProfileException extends RuntimeException {
    public IncompleteProfileException() {
        super("El perfil necesita nombre, resumen y al menos un rol objetivo para solicitar revisión");
    }
}
