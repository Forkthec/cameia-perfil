package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import java.util.UUID;

/**
 * Contrato versionado del evento perfil-profesional-actualizado (v1).
 *
 * Consumidores externos (Entrevista, Empleo) deserializan este record.
 * No romper compatibilidad: solo agregar campos opcionales en v1;
 * cambios de estructura → crear ProfileUpdatedPayloadV2.
 *
 * TODO CM-XXX: agregar correlationId (trazabilidad entre microservicios, reglas §13).
 * TODO CM-XXX: validar con el equipo los campos que necesitan Entrevista y Empleo.
 */
public record ProfileUpdatedPayloadV1(
        UUID profileId,
        String firebaseUid,
        String name,
        String status
) {}
