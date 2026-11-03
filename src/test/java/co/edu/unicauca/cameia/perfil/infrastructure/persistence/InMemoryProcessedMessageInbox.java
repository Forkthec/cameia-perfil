package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.port.ProcessedMessageInbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Doble en memoria del Inbox para las pruebas unitarias: guarda el tipo de cada mensaje anotado. */
public class InMemoryProcessedMessageInbox implements ProcessedMessageInbox {

    private final Map<UUID, String> entries = new ConcurrentHashMap<>();

    @Override
    public boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt) {
        return entries.putIfAbsent(messageId, eventType) == null;
    }

    /** @return copia de los mensajes anotados, por identificador, con su tipo */
    public Map<UUID, String> contents() {
        return Map.copyOf(entries);
    }
}
