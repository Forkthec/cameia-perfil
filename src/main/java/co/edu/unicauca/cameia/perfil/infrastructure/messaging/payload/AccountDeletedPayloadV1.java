package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Campo que este servicio lee de {@code cuenta.eliminada} versión 1; el instante de eliminación no se enlaza.
 *
 * @param userId identidad del Usuario (el {@code firebaseUid}), campo {@code usuarioId} del contrato
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDeletedPayloadV1(@JsonProperty("usuarioId") String userId) {
}
