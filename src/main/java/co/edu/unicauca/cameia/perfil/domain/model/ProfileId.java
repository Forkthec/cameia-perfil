package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidad del agregado ProfessionalProfile.
 *
 * <p>Wrappear el UUID evita pasar un UUID de otro agregado donde se espera un ProfileId.
 * El compilador detecta la confusión; un String o UUID suelto no.
 */
public record ProfileId(UUID value) {

    public ProfileId {
        Objects.requireNonNull(value, "ProfileId no puede ser nulo");
    }

    public static ProfileId generate() {
        return new ProfileId(UUID.randomUUID());
    }

    public static ProfileId of(UUID value) {
        return new ProfileId(value);
    }
}
