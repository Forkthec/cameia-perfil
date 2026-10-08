package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.List;

/** Se lanza cuando el perfil no cumple los requisitos mínimos de completitud (CM-22). */
public class IncompleteProfileException extends BusinessException {

    private final List<String> missingRequirements;

    public IncompleteProfileException(List<String> missingRequirements) {
        super(ErrorCode.PROFILE_INCOMPLETE, "Todavía no cumples estos requisitos:");
        this.missingRequirements = List.copyOf(missingRequirements);
    }

    public List<String> getMissingRequirements() { return missingRequirements; }
}
