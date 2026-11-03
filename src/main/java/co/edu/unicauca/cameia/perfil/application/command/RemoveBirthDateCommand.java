package co.edu.unicauca.cameia.perfil.application.command;

/**
 * Datos de un evento de cuenta eliminada, tal como llegan en el mensaje. Los valores vienen crudos: el servicio los valida.
 *
 * @param messageId identificador del mensaje, puesto por el productor
 * @param userId    identidad del Usuario (el {@code firebaseUid})
 */
public record RemoveBirthDateCommand(String messageId, String userId) {
}
