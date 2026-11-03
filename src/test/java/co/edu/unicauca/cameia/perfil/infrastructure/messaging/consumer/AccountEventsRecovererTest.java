package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Al agotar los intentos, el recuperador deja un único ERROR con traza y rechaza el mensaje sin reencolarlo. */
class AccountEventsRecovererTest {

    private final AccountEventsRecoverer recoverer = new AccountEventsRecoverer();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccountEventsRecoverer.class);

    @BeforeEach
    void attachAppender() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("Un mensaje agotado se registra una vez en ERROR con traza y sin su cuerpo, y se rechaza sin reencolar")
    void recover_shouldLogErrorOnceAndRejectWithoutRequeue_whenAttemptsAreExhausted() {
        var properties = new MessageProperties();
        properties.setMessageId("44444444-4444-4444-8444-444444444444");
        properties.setConsumerQueue("perfil.cuenta-eliminada");
        var message = new Message("{\"usuarioId\":\"secreto\"}".getBytes(), properties);
        var cause = new DataAccessResourceFailureException("base de datos caída");

        assertThatThrownBy(() -> recoverer.recover(message, cause))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getFormattedMessage()).contains("cola=perfil.cuenta-eliminada",
                    "messageId=44444444-4444-4444-8444-444444444444", "causa=DataAccessResourceFailureException")
                    .doesNotContain("secreto");
            // La traza conserva la clase y la pila, pero no el texto de la excepción.
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo(DataAccessResourceFailureException.class.getName());
            assertThat(event.getThrowableProxy().getMessage()).doesNotContain("base de datos caída");
        });
    }

    @Test
    @DisplayName("Un mensaje que incumple el contrato ya se registró en WARN: el recuperador solo lo rechaza")
    void recover_shouldOnlyReject_whenFailureIsContractRejection() {
        var message = new Message("{}".getBytes(), new MessageProperties());
        var rejection = new ListenerExecutionFailedException("fallo", new AmqpRejectAndDontRequeueException("USER_ID_INVALID"), message);

        assertThatThrownBy(() -> recoverer.recover(message, rejection))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(logs.list).isEmpty();
    }
}
