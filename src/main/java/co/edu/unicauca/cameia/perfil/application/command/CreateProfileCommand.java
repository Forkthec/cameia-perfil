package co.edu.unicauca.cameia.perfil.application.command;

/**
 * Intención de crear un perfil profesional nuevo.
 *
 * <p>{@code firebaseUid} llega del header {@code X-User-Id} propagado por el Gateway.
 * El controller lo extrae y construye este comando; el app service verifica que no venga vacío.
 */
public record CreateProfileCommand(String firebaseUid) { }
