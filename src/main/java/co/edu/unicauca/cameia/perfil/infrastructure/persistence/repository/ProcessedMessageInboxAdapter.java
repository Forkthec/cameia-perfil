package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.port.ProcessedMessageInbox;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Implementa el Inbox sobre PostgreSQL: la clave primaria del mensaje decide, sin carreras, quién lo anota primero. */
@Repository
class ProcessedMessageInboxAdapter implements ProcessedMessageInbox {

    private final ProcessedMessageJpaRepository jpa;

    ProcessedMessageInboxAdapter(ProcessedMessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt) {
        return jpa.insertIfAbsent(messageId, eventType, processedAt) == 1;
    }
}
