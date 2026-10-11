package co.edu.unicauca.cameia.perfil.infrastructure.messaging.publisher;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El publicador cuenta un evento como publicado solo con confirmación positiva, sin devolución y dentro del plazo. */
class RabbitEventPublisherTest {

    private static final UUID EVENT_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final OutboundEvent EVENT = new OutboundEvent(EVENT_ID, "perfil.profesional-actualizado", 1,
            UUID.fromString("1a2b3c4d-1111-4222-8333-444455556666"), "{\"usuarioId\":\"u1\"}", "req-1",
            Instant.parse("2026-10-10T12:00:00.123Z"));

    private final RabbitTemplate template = mock(RabbitTemplate.class);

    /** El broker responde con la confirmación dada después de la espera dada (cero = al instante). */
    private void brokerAnswers(boolean ack, long delayMillis, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE", "perfil.events", "x"));
            }
            if (delayMillis == 0) {
                correlation.getFuture().complete(new CorrelationData.Confirm(ack, null));
            } else {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    correlation.getFuture().complete(new CorrelationData.Confirm(ack, null));
                });
            }
            return null;
        }).when(template).send(eq("perfil.events"), eq("perfil.profesional-actualizado"), any(Message.class), any(CorrelationData.class));
    }

    private RabbitEventPublisher publisher() {
        return new RabbitEventPublisher(template, RabbitEventPublisher.DEFAULT_TIMEOUT);
    }

    @Test
    @DisplayName("Una confirmación positiva sin devolución cuenta como publicado")
    void publish_shouldReturnTrue_whenBrokerConfirms() {
        brokerAnswers(true, 0, false);

        assertThat(publisher().publish(EVENT)).isTrue();
    }

    @Test
    @DisplayName("Una confirmación negativa deja el evento sin publicar")
    void publish_shouldReturnFalse_whenBrokerNacks() {
        brokerAnswers(false, 0, false);

        assertThat(publisher().publish(EVENT)).isFalse();
    }

    @Test
    @DisplayName("Un mensaje devuelto por no tener cola de destino no cuenta como publicado aunque el broker confirme")
    void publish_shouldReturnFalse_whenMessageIsReturned() {
        brokerAnswers(true, 0, true);

        assertThat(publisher().publish(EVENT)).isFalse();
    }

    @Test
    @DisplayName("Una confirmación que tarda más del plazo deja el evento sin publicar sin esperar más de 550 ms")
    void publish_shouldReturnFalse_whenConfirmationTakesLongerThanLimit() {
        brokerAnswers(true, 600, false);

        long started = System.nanoTime();
        boolean published = publisher().publish(EVENT);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(published).isFalse();
        assertThat(elapsedMillis).isLessThanOrEqualTo(550);
    }

    @Test
    @DisplayName("Un fallo de conexión con el broker deja el evento sin publicar y no sale como excepción")
    void publish_shouldReturnFalse_whenBrokerIsDown() {
        doThrow(new AmqpConnectException(new IOException("sin conexión")))
                .when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher().publish(EVENT)).isFalse();
    }

    @Test
    @DisplayName("El mensaje lleva las propiedades AMQP del contrato y la carga tal como se guardó")
    void publish_shouldSendContractProperties_whenEventIsPublished() {
        brokerAnswers(true, 0, false);

        publisher().publish(EVENT);

        var message = ArgumentCaptor.forClass(Message.class);
        var correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).send(eq("perfil.events"), eq("perfil.profesional-actualizado"), message.capture(), correlation.capture());
        var properties = message.getValue().getMessageProperties();
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"usuarioId\":\"u1\"}");
        assertThat(properties.getMessageId()).isEqualTo(EVENT_ID.toString());
        assertThat(properties.getType()).isEqualTo("perfil.profesional-actualizado");
        assertThat(properties.getAppId()).isEqualTo("cameia-perfil");
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getCorrelationId()).isEqualTo("req-1");
        assertThat(properties.getHeaders()).containsEntry("x-event-version", 1).containsEntry("x-causation-id", "req-1");
        assertThat(properties.getTimestamp().toInstant()).isEqualTo(Instant.parse("2026-10-10T12:00:00.123Z"));
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(correlation.getValue().getId()).isEqualTo(EVENT_ID.toString());
    }
}
