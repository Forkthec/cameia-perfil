package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.event.RelaySummary;
import co.edu.unicauca.cameia.perfil.domain.port.EventPublisher;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Publica los eventos de la tabla de salida y deja registrado el resultado de cada intento.
 *
 * <p>Hay dos caminos. Tras el commit de una escritura se intenta publicar su evento una vez, sin transacción abierta
 * durante la llamada al broker; el resultado se anota en una transacción corta aparte. El Job periódico toma los
 * pendientes con bloqueo de fila (las filas que otra instancia ya tomó se saltan) y los publica en una transacción por
 * lote, acotada por el plazo de confirmación de cada evento. El contrato es de al menos una vez: los consumidores son
 * idempotentes.</p>
 */
@Service
public class OutboxRelayAppService {

    static final int BATCH_SIZE = 50;
    static final int MAX_BATCHES = 10;
    static final int ALERT_ATTEMPTS = 10;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayAppService.class);

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;

    OutboxRelayAppService(OutboxRepository outbox, EventPublisher publisher, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.required = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Intenta publicar el evento una vez, justo después de que se confirme la transacción en curso.
     *
     * <p>Un fallo aquí nunca cambia la respuesta de la petición: se registra en {@code WARN} y la fila queda pendiente
     * para el Job. Sin transacción en curso, publica de inmediato.</p>
     *
     * @param event evento que acaba de guardarse en la tabla de salida
     */
    public void relayAfterCommit(OutboundEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            relayOne(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relayOne(event);
            }
        });
    }

    /**
     * Publica los pendientes, el más antiguo primero, hasta vaciarlos o llegar a {@value #MAX_BATCHES} lotes.
     *
     * <p>Un lote con algún fallo corta la corrida: los eventos fallidos siguen siendo los más antiguos y se leerían de
     * nuevo en el lote siguiente, con lo que un mismo evento sumaría muchos intentos en una sola corrida y dispararía la
     * alerta sin motivo. El Job vuelve a intentarlo en su próxima ejecución.</p>
     *
     * @return cuántos se publicaron, cuántos intentos fallaron y cuántos siguen pendientes
     */
    public RelaySummary relayPending() {
        int published = 0;
        int failed = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int[] result = required.execute(status -> relayBatch());
            published += result[0];
            failed += result[1];
            if (result[1] > 0 || result[0] + result[1] < BATCH_SIZE) {
                break;
            }
        }
        return new RelaySummary(published, failed, outbox.countPending());
    }

    /** Toma un lote con bloqueo y publica cada evento; devuelve {publicados, fallidos}. */
    private int[] relayBatch() {
        List<OutboundEvent> events = outbox.lockPending(BATCH_SIZE);
        int published = 0;
        int failed = 0;
        for (OutboundEvent event : events) {
            if (publishAndRecord(event)) {
                published++;
            } else {
                failed++;
            }
        }
        return new int[] {published, failed};
    }

    /** Camino posterior al commit: un intento, el resultado en una transacción corta y nunca una excepción hacia fuera. */
    private void relayOne(OutboundEvent event) {
        try {
            boolean confirmed = publisher.publish(event);
            requiresNew.executeWithoutResult(status -> record(event, confirmed));
        } catch (RuntimeException failure) {
            // La fila sigue pendiente: la recoge el Job. Sin la carga en el log.
            log.warn("No se pudo publicar ni anotar el evento tras el commit [eventId={}, type={}, causa={}]",
                    event.id(), event.type(), failure.getClass().getSimpleName());
        }
    }

    /** Publica un evento del lote y anota el resultado; una excepción del publicador cuenta como no confirmado. */
    private boolean publishAndRecord(OutboundEvent event) {
        boolean confirmed;
        try {
            confirmed = publisher.publish(event);
        } catch (RuntimeException failure) {
            log.warn("El publicador falló con el evento [eventId={}, type={}, causa={}]",
                    event.id(), event.type(), failure.getClass().getSimpleName());
            confirmed = false;
        }
        record(event, confirmed);
        return confirmed;
    }

    /** Marca el evento como publicado o suma un intento; avisa una sola vez, al llegar al décimo. */
    private void record(OutboundEvent event, boolean confirmed) {
        if (confirmed) {
            outbox.markPublished(event.id(), clock.instant());
            return;
        }
        if (outbox.addAttempt(event.id()) == ALERT_ATTEMPTS) {
            log.error("Evento sin publicar tras {} intentos [eventId={}, type={}]", ALERT_ATTEMPTS, event.id(), event.type());
        }
    }
}
