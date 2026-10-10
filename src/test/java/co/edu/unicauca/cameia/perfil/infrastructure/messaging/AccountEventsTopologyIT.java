package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.rabbitmq.client.ShutdownSignalException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Topología que el servicio declara en un RabbitMQ real: los nombres del contrato, las colas de fallidos y los argumentos
 * de fallidos de las dos colas principales.
 */
class AccountEventsTopologyIT extends MessagingIntegrationSupport {

    @Autowired
    @Qualifier("accountCreatedQueue")
    Queue accountCreatedQueue;

    @Test
    @DisplayName("Al arrancar existen las cuatro colas del contrato")
    void topology_shouldDeclareContractNames_whenStarted() {
        for (var queue : new String[] {CREATED_QUEUE, DELETED_QUEUE, CREATED_DLQ, DELETED_DLQ}) {
            assertThat(rabbitAdmin.getQueueInfo(queue)).as(queue).isNotNull();
        }
    }

    /**
     * RabbitMQ no devuelve los argumentos de una cola por AMQP; se comprueban volviendo a declararla: con sus argumentos
     * reales la declaración es idempotente y sin ellos el broker responde {@code PRECONDITION_FAILED}.
     */
    @Test
    @DisplayName("Las colas principales llevan perfil.dlx y su propio nombre como destino de los fallidos")
    void topology_shouldRouteRejectionsToDeadLetterExchange_whenQueueIsDeclared() {
        assertThat(accountCreatedQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", "perfil.dlx")
                .containsEntry("x-dead-letter-routing-key", CREATED_QUEUE);

        rabbitTemplate.execute(channel ->
                channel.queueDeclare(CREATED_QUEUE, true, false, false, accountCreatedQueue.getArguments()));

        assertThatThrownBy(() -> rabbitTemplate.execute(channel ->
                channel.queueDeclare(CREATED_QUEUE, true, false, false, Map.of())))
                .isInstanceOf(AmqpIOException.class)
                .rootCause().isInstanceOf(ShutdownSignalException.class).hasMessageContaining("PRECONDITION_FAILED");
    }

    @Test
    @DisplayName("La cola antigua cuenta-eliminada ya no se declara")
    void topology_shouldNotDeclareLegacyAccountDeletedQueue_whenStarted() {
        assertThat(rabbitAdmin.getQueueInfo("cuenta-eliminada")).isNull();
    }
}
