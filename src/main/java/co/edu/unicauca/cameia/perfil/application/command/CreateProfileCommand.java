package co.edu.unicauca.cameia.perfil.application.command;

/**
 * Intención de crear un perfil profesional nuevo (CM-16).
 *
 * <p>{@code firebaseUid} llega del header {@code X-User-Id} propagado por el Gateway.
 * El controller lo extrae y construye este comando antes de llamar al app service.
 */
public record CreateProfileCommand(String firebaseUid) {

    public CreateProfileCommand {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new IllegalArgumentException("firebaseUid no puede estar vacío en CreateProfileCommand");
        }
    }
}
