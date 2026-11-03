package co.edu.unicauca.cameia.perfil.application.command;

import java.time.LocalDate;

/**
 * Datos de un evento de cuenta creada, tal como llegan en el mensaje. Los valores vienen crudos: el servicio los valida.
 *
 * @param messageId  identificador del mensaje, puesto por el productor
 * @param userId     identidad del Usuario (el {@code firebaseUid})
 * @param birthDate  fecha de nacimiento del Usuario
 */
public record ReplicateBirthDateCommand(String messageId, String userId, LocalDate birthDate) {
}
