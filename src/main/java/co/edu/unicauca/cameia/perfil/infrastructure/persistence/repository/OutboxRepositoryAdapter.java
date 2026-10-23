package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.RoleCatalogPayloadV1;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementa el puerto {@link OutboxRepository} sobre PostgreSQL.
 *
 * <p>El relevo no puede mantener una transacción abierta mientras espera al broker después de una escritura, así que
 * cada operación que escribe declara su propia transacción: cuando la llama el servicio de perfiles se une a la del
 * cambio, y cuando la llama el relevo abre una corta. Agregar y leer con bloqueo exigen la transacción del llamador.</p>
 *
 * <p>La carga del evento de perfil y la del catálogo de roles se arman aquí, porque el contrato JSON es asunto de la
 * infraestructura.</p>
 */
@Repository
class OutboxRepositoryAdapter implements OutboxRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    public OutboundEvent appendProfileUpdated(ProfileUpdated event) {
        var stored = new OutboundEvent(event.eventId(), ProfileUpdated.TYPE, ProfileUpdated.VERSION,
                event.profile().getId().value(), JSON.writeValueAsString(ProfileUpdatedPayloadV1.from(event)),
                event.correlationId().value(), event.updatedAt());
        append(stored);
        return stored;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboundEvent saveRoleCatalog(RoleCatalogUpdated event) {
        var stored = new OutboundEvent(event.eventId(), RoleCatalogUpdated.TYPE, RoleCatalogUpdated.VERSION,
                RoleCatalogUpdated.AGGREGATE_ID, JSON.writeValueAsString(RoleCatalogPayloadV1.from(event)),
                event.correlationId().value(), event.updatedAt());
        append(stored);
        return stored;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean existsPendingOfType(String type) {
        return repository.existsPendingOfType(type);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryLockAnnouncement(String type) {
        return repository.tryAdvisoryLock(type);
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
