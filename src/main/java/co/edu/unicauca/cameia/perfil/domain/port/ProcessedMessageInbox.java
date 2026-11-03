package co.edu.unicauca.cameia.perfil.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de los mensajes de eventos de cuenta ya procesados, para que una reentrega no tenga efecto.
 *
 * <p>Los mensajes llegan al menos una vez. Anotar el identificador del mensaje en la misma transacción que su efecto hace que
 * el efecto ocurra una sola vez aunque el broker entregue el mensaje otra vez después del commit.</p>
 */
public interface ProcessedMessageInbox {

    /**
     * Anota el mensaje como procesado si no lo estaba.
     *
     * @param messageId   identificador del mensaje, que pone el productor
     * @param eventType   {@code cuenta.creada} o {@code cuenta.eliminada}
     * @param processedAt instante del procesamiento, en UTC
     * @return true si el mensaje no estaba registrado y ahora lo está; false si ya se había procesado
     */
    boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt);
}
