package co.edu.unicauca.cameia.perfil.domain.exception;

import java.util.List;

/** Se lanza cuando el perfil no cumple los requisitos mínimos para finalizarlo; lleva la lista de los que faltan. */
public class IncompleteProfileException extends BusinessException {

    private final List<String> missingRequirements;

    /** @param missingRequirements requisitos que faltan, en el orden en que se revisan */
    public IncompleteProfileException(List<String> missingRequirements) {
        super(ErrorCode.PROFILE_INCOMPLETE, "Todavía no cumples estos requisitos:");
        this.missingRequirements = List.copyOf(missingRequirements);
    }

    /** @return copia inmutable de los requisitos que faltan */
    public List<String> getMissingRequirements() { return missingRequirements; }
}
