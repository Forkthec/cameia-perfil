package co.edu.unicauca.cameia.perfil.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.port.EventPublisher;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El relevo publica, marca o suma intentos, avisa una sola vez al décimo y nunca deja caer una excepción hacia la petición. */
class OutboxRelayAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    /** Tareas que el relevo entregó al ejecutor posterior al commit, sin correrlas todavía. */
    private final List<Runnable> deferred = new ArrayList<>();
    private final OutboxRelayAppService service = new OutboxRelayAppService(outbox, publisher,
            Clock.fixed(NOW, ZoneOffset.UTC), transactionManager, deferred::add);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(OutboxRelayAppService.class)).addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        ((Logger) LoggerFactory.getLogger(OutboxRelayAppService.class)).detachAppender(logs);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static OutboundEvent event() {
        return new OutboundEvent(UUID.randomUUID(), "perfil.profesional-actualizado", 1, UUID.randomUUID(),
                "{\"nombre\":\"Ana Pérez\"}", "req-1", NOW);
    }

    private long errorsLogged() {
        return logs.list.stream().filter(entry -> entry.getLevel() == Level.ERROR).count();
    }

    @Test
    @DisplayName("Sin transacción en curso, un evento confirmado se marca como publicado")
    void relayAfterCommit_shouldMarkPublished_whenNoTransactionIsActive() {
        var event = event();
        when(publisher.publish(event)).thenReturn(true);

        service.relayAfterCommit(event);

        verify(outbox).markPublished(event.id(), NOW);
    }

    @Test
    @DisplayName("Con una transacción en curso no publica hasta el commit")
    void relayAfterCommit_shouldWaitForCommit_whenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        var event = event();
        when(publisher.publish(event)).thenReturn(true);

        service.relayAfterCommit(event);

        verify(publisher, never()).publish(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        // El intento sale del hilo de la petición: sigue sin publicar hasta que el ejecutor lo corra.
        verify(publisher, never()).publish(any());
        assertThat(deferred).hasSize(1);
        deferred.get(0).run();
        verify(publisher).publish(event);
        verify(outbox).markPublished(event.id(), NOW);
    }

    @Test
    @DisplayName("Si el ejecutor rechaza el intento, la petición no lo ve y la fila queda pendiente para el Job")
    void relayAfterCommit_shouldKeepRowPending_whenExecutorRejects() {
        var rejecting = new OutboxRelayAppService(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager, task -> {
                    throw new RejectedExecutionException("cola llena");
                });
        TransactionSynchronizationManager.initSynchronization();
        var event = event();

        rejecting.relayAfterCommit(event);
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());

        verify(publisher, never()).publish(any());
        verify(outbox, never()).markPublished(any(), any());
        assertThat(logs.list).anySatisfy(entry -> {
            assertThat(entry.getLevel()).isEqualTo(Level.WARN);
            assertThat(entry.getFormattedMessage()).contains(event.id().toString());
        });
    }

    @Test
    @DisplayName("Si el broker no confirma, la fila queda pendiente con un intento más")
    void relayAfterCommit_shouldAddAttempt_whenBrokerDoesNotConfirm() {
        var event = event();
        when(publisher.publish(event)).thenReturn(false);

        service.relayAfterCommit(event);

        verify(outbox).addAttempt(event.id());
        verify(outbox, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("Si el publicador lanza una excepción, la petición no la ve y la fila queda pendiente")
    void relayAfterCommit_shouldKeepRowPending_whenBrokerFails() {
        var event = event();
        when(publisher.publish(event)).thenThrow(new IllegalStateException("el broker cayó"));

        service.relayAfterCommit(event);

        verify(outbox, never()).markPublished(any(), any());
        assertThat(logs.list).anySatisfy(entry -> {
            assertThat(entry.getLevel()).isEqualTo(Level.WARN);
            assertThat(entry.getFormattedMessage()).contains(event.id().toString()).doesNotContain("Ana Pérez");
        });
    }

    @Test
    @DisplayName("El décimo intento fallido registra un solo ERROR con el id y el tipo, sin la carga")
    void relayAfterCommit_shouldLogErrorOnce_whenTenthAttemptFails() {
        var event = event();
        when(publisher.publish(event)).thenReturn(false);
        when(outbox.addAttempt(event.id())).thenReturn(9, 10, 11);

        service.relayAfterCommit(event);
        assertThat(errorsLogged()).isZero();
        service.relayAfterCommit(event);
        service.relayAfterCommit(event);

        assertThat(errorsLogged()).isEqualTo(1);
        assertThat(logs.list).filteredOn(entry -> entry.getLevel() == Level.ERROR).singleElement().satisfies(entry ->
                assertThat(entry.getFormattedMessage()).contains(event.id().toString())
                        .contains("perfil.profesional-actualizado").doesNotContain("Ana Pérez"));
    }

    @Test
    @DisplayName("Sin pendientes, la corrida no publica nada y devuelve ceros")
    void relayPending_shouldReturnZeros_whenNothingIsPending() {
        when(outbox.lockPending(anyInt())).thenReturn(List.of());
        when(outbox.countPending()).thenReturn(0L);

        var summary = service.relayPending();

        assertThat(summary.published()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(summary.stillPending()).isZero();
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Un lote con un fallo sigue con las demás filas, cuenta el fallo y corta la corrida")
    void relayPending_shouldContinueWithOthers_whenOneEventFails() {
        var first = event();
        var broken = event();
        var last = event();
        when(outbox.lockPending(OutboxRelayAppService.BATCH_SIZE)).thenReturn(List.of(first, broken, last));
        when(publisher.publish(first)).thenReturn(true);
        when(publisher.publish(broken)).thenThrow(new IllegalStateException("fallo"));
        when(publisher.publish(last)).thenReturn(true);
        when(outbox.countPending()).thenReturn(1L);

        var summary = service.relayPending();

        assertThat(summary.published()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.stillPending()).isEqualTo(1);
        verify(outbox).markPublished(first.id(), NOW);
        verify(outbox).markPublished(last.id(), NOW);
        verify(outbox).addAttempt(broken.id());
    }

    @Test
    @DisplayName("Con lotes llenos sigue leyendo hasta vaciar y se detiene a los 10 lotes")
    void relayPending_shouldStopAtMaxBatches_whenEveryBatchIsFull() {
        var batch = new ArrayList<OutboundEvent>();
        IntStream.range(0, OutboxRelayAppService.BATCH_SIZE).forEach(i -> batch.add(event()));
        when(outbox.lockPending(OutboxRelayAppService.BATCH_SIZE)).thenReturn(batch);
        when(publisher.publish(any())).thenReturn(true);
        when(outbox.countPending()).thenReturn(7L);

        var summary = service.relayPending();

        assertThat(summary.published()).isEqualTo(OutboxRelayAppService.BATCH_SIZE * OutboxRelayAppService.MAX_BATCHES);
        verify(outbox, org.mockito.Mockito.times(OutboxRelayAppService.MAX_BATCHES)).lockPending(OutboxRelayAppService.BATCH_SIZE);
    }
}
