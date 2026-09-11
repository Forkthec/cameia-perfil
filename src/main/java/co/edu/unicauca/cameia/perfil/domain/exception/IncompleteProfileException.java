package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.List;

/** Se lanza cuando el perfil no cumple los requisitos mínimos de completitud (CM-22). */
public class IncompleteProfileException extends RuntimeException {

    private final List<String> missingRequirements;

    public IncompleteProfileException(List<String> missingRequirements) {
        super("El perfil no cumple los requisitos mínimos: " + missingRequirements);
        this.missingRequirements = List.copyOf(missingRequirements);
    }

    public List<String> getMissingRequirements() { return missingRequirements; }
}
