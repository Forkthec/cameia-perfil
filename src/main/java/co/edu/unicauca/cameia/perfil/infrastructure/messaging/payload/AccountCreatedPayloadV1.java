package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

import java.time.LocalDate;

/**
 * Campos que este servicio lee de {@code cuenta.creada} versión 1. El correo y el instante de creación no se declaran a propósito:
 * aquí nunca se enlazan, ni se guardan, ni se registran.
 *
 * @param userId    identidad del Usuario (el {@code firebaseUid}), campo {@code usuarioId} del contrato
 * @param birthDate fecha de nacimiento, campo {@code fechaNacimiento} del contrato; se lee en modo estricto, así que un texto
 *                  con hora o con otro formato se rechaza en lugar de recortarse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedPayloadV1(
        @JsonProperty("usuarioId") String userId,
        @JsonProperty("fechaNacimiento") @JsonFormat(lenient = OptBoolean.FALSE) LocalDate birthDate) {
}
