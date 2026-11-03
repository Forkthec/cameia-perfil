package co.edu.unicauca.cameia.perfil.domain.exception;

import co.edu.unicauca.cameia.perfil.domain.model.CompletionRequirement;

import java.util.List;

/** Se lanza cuando el perfil no cumple los requisitos mínimos para finalizarlo; lleva la lista de los que faltan. */
public class IncompleteProfileException extends BusinessException {

    private final List<CompletionRequirement> missingRequirements;

    /** @param missingRequirements requisitos que faltan, en el orden en que se revisan */
    public IncompleteProfileException(List<CompletionRequirement> missingRequirements) {
        super(ErrorCode.PROFILE_INCOMPLETE, "Todavía no cumples estos requisitos:");
        this.missingRequirements = List.copyOf(missingRequirements);
    }

    /** @return copia inmutable de los requisitos que faltan */
    public List<CompletionRequirement> getMissingRequirements() { return missingRequirements; }
}
