package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila del Inbox: un mensaje de evento de cuenta ya procesado. No guarda datos personales.
 *
 * <p>La entidad existe para que {@code ddl-auto=validate} compruebe la tabla; la escritura es la consulta nativa del repositorio.</p>
 */
@Entity
@Table(name = "evento_procesado")
public class ProcessedMessageEntity {

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "tipo", nullable = false, length = 32)
    private String eventType;

    @Column(name = "procesado_en", nullable = false)
    private Instant processedAt;

    /** Constructor para JPA. */
    protected ProcessedMessageEntity() {
    }

    /** @return identificador del mensaje */
    public UUID getMessageId() {
        return messageId;
    }

    /** @return tipo del evento, {@code cuenta.creada} o {@code cuenta.eliminada} */
    public String getEventType() {
        return eventType;
    }

    /** @return instante del procesamiento, en UTC */
    public Instant getProcessedAt() {
        return processedAt;
    }
}
