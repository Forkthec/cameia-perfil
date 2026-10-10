package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementa el puerto {@link OutboxRepository} sobre PostgreSQL.
 *
 * <p>El relevo no puede mantener una transacción abierta mientras espera al broker después de una escritura, así que
 * cada operación que escribe declara su propia transacción: cuando la llama el servicio de perfiles se une a la del
 * cambio, y cuando la llama el relevo abre una corta. Agregar y leer con bloqueo exigen la transacción del llamador.</p>
 */
@Repository
class OutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxEventJpaRepository repository;

    OutboxRepositoryAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboundEvent event) {
        repository.insert(event.id(), event.type(), (short) event.version(), event.aggregateId(), event.payloadJson(),
                event.correlationId(), event.createdAt());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboundEvent> lockPending(int limit) {
        return repository.lockPending(limit).stream().map(OutboxRepositoryAdapter::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        repository.markPublished(id, publishedAt);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public int addAttempt(UUID id) {
        var attempts = repository.incrementAttempts(id);
        return attempts == null ? 0 : attempts;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countPending();
    }

    private static OutboundEvent toDomain(OutboxEventEntity entity) {
        return new OutboundEvent(entity.getId(), entity.getType(), entity.getVersion(), entity.getAggregateId(),
                entity.getPayload(), entity.getCorrelationId(), entity.getCreatedAt());
    }
}
