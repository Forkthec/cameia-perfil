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
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El manejador de errores manda al fallidos lo que incumple el contrato y deja reintentar lo técnico, sin datos del mensaje. */
class AccountEventsErrorHandlerTest {

    private final AccountEventsErrorHandler handler = new AccountEventsErrorHandler();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccountEventsErrorHandler.class);

    @BeforeEach
    void attachAppender() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(logs);
    }

    private static Message amqpMessage(String messageId) {
        var properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setConsumerQueue("perfil.cuenta-creada");
        return new Message("{\"fechaNacimiento\":\"2008-03-15\"}".getBytes(), properties);
    }

    private static ListenerExecutionFailedException failure(Throwable cause) {
        return new ListenerExecutionFailedException("fallo del consumidor", cause);
    }

    @Test
    @DisplayName("Un cuerpo que no se pudo convertir se rechaza sin reencolar y se registra una vez en WARN")
    void handleError_shouldRejectWithoutRequeue_whenBodyCannotBeConverted() {
        var failure = failure(new MessageConversionException("no es json"));

        assertThatThrownBy(() -> handler.handleError(amqpMessage("33333333-3333-4333-8333-333333333333"), null, null, failure))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_INVALID_FORMAT");

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("cola=perfil.cuenta-creada",
                    "messageId=33333333-3333-4333-8333-333333333333", "razón=PAYLOAD_INVALID_FORMAT");
            assertThat(event.getFormattedMessage()).doesNotContain("2008-03-15").doesNotContain("no es json");
        });
    }

    @Test
    @DisplayName("Una falla de conversión del modelo de mensajes de Spring también se rechaza como formato inválido")
    void handleError_shouldRejectWithoutRequeue_whenSpringMessagingConversionFails() {
        var failure = failure(new org.springframework.messaging.converter.MessageConversionException("tipo equivocado"));

        assertThatThrownBy(() -> handler.handleError(amqpMessage(null), null, null, failure))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_INVALID_FORMAT");
        assertThat(logs.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("messageId=-"));
    }

    @Test
    @DisplayName("Un cuerpo que no se puede enlazar con el parámetro del consumidor se rechaza como formato inválido")
    void handleError_shouldRejectWithoutRequeue_whenArgumentCannotBeResolved() throws Exception {
        var parameter = new MethodParameter(AccountCreatedListener.class.getMethod("onAccountCreated",
                co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.AccountCreatedPayloadV1.class, String.class), 0);
        var failure = failure(new MethodArgumentResolutionException(MessageBuilder.withPayload("x").build(), parameter, "sin cuerpo"));

        assertThatThrownBy(() -> handler.handleError(amqpMessage("33333333-3333-4333-8333-333333333333"), null, null, failure))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Una cadena de causas de más de 32 niveles no se recorre entera y el fallo se trata como técnico")
    void handleError_shouldTreatAsTechnical_whenCauseChainIsLongerThanTheLimit() {
        Throwable chain = new MessageConversionException("al fondo de la cadena");
        for (int i = 0; i < 40; i++) {
            chain = new IllegalStateException("nivel " + i, chain);
        }
        var failure = failure(chain);

        assertThatThrownBy(() -> handler.handleError(amqpMessage("x"), null, null, failure)).isSameAs(failure);
        assertThat(AccountEventsErrorHandler.hasCause(chain, MessageConversionException.class)).isFalse();
        assertThat(AccountEventsErrorHandler.rootCauseName(chain)).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("Un rechazo que el consumidor ya registró se relanza sin registrarlo otra vez")
    void handleError_shouldRethrowRejection_whenConsumerAlreadyRejected() {
        var rejection = new AmqpRejectAndDontRequeueException("USER_ID_INVALID");

        assertThatThrownBy(() -> handler.handleError(amqpMessage("x"), null, null, failure(rejection)))
                .isSameAs(rejection);
        assertThat(logs.list).isEmpty();
    }

    @Test
    @DisplayName("Un fallo técnico se relanza para el reintento y se anota en WARN sin traza ni datos del mensaje")
    void handleError_shouldRethrowFailure_whenFailureIsTechnical() {
        var failure = failure(new DataAccessResourceFailureException("base de datos caída"));

        assertThatThrownBy(() -> handler.handleError(amqpMessage("a\nb"), null, null, failure)).isSameAs(failure);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).contains("messageId=invalid", "causa=DataAccessResourceFailureException")
                    .doesNotContain("2008-03-15").doesNotContain("base de datos caída");
        });
    }

    @Test
    @DisplayName("La causa raíz se nombra por su clase simple, aunque el fallo no tenga causa")
    void rootCauseName_shouldReturnSimpleClassName_whenFailureHasNoCause() {
        assertThat(AccountEventsErrorHandler.rootCauseName(new IllegalStateException("x"))).isEqualTo("IllegalStateException");
        assertThat(AccountEventsErrorHandler.rootCauseName(failure(new IllegalStateException("x"))))
                .isEqualTo("IllegalStateException");
    }
}
