package co.edu.unicauca.cameia.perfil.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de salida tal como lo necesita el relevo: la fila pendiente de la tabla de salida.
 *
 * @param id            identificador del evento, también el {@code message_id} de AMQP
 * @param type          tipo del evento, también la clave de enrutamiento (por ejemplo {@code perfil.profesional-actualizado})
 * @param version       versión del contrato de la carga
 * @param aggregateId   identificador del perfil al que pertenece
 * @param payloadJson   carga JSON ya serializada; no se escribe nunca en el log
 * @param correlationId correlación con la petición que lo originó
 * @param createdAt     instante en que se registró el evento
 */
public record OutboundEvent(UUID id, String type, int version, UUID aggregateId, String payloadJson,
        String correlationId, Instant createdAt) {

    /**
     * Comprueba que están todos los componentes.
     *
     * @throws NullPointerException si algún componente es nulo
     */
    public OutboundEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
