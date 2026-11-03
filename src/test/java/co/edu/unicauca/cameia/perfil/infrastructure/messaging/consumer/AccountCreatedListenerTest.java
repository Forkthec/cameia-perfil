package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unicauca.cameia.perfil.application.command.ReplicateBirthDateCommand;
import co.edu.unicauca.cameia.perfil.application.service.BirthDateReplicaAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException.Reason;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.AccountCreatedPayloadV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El consumidor de {@code cuenta.creada} traduce el mensaje en un comando y clasifica sus fallos, sin escribir datos personales. */
class AccountCreatedListenerTest {

    private static final String MESSAGE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String USER_ID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2008, 3, 15);

    private final BirthDateReplicaAppService service = mock(BirthDateReplicaAppService.class);
    private final AccountCreatedListener listener = new AccountCreatedListener(service);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccountCreatedListener.class);

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
    @DisplayName("Un evento válido llega al servicio con el identificador del mensaje, la identidad y la fecha")
    void onAccountCreated_shouldCallService_whenPayloadIsValid() {
        listener.onAccountCreated(new AccountCreatedPayloadV1(USER_ID, BIRTH_DATE), MESSAGE_ID);

        var command = ArgumentCaptor.forClass(ReplicateBirthDateCommand.class);
        verify(service).recordAccountCreated(command.capture());
        assertThat(command.getValue()).isEqualTo(new ReplicateBirthDateCommand(MESSAGE_ID, USER_ID, BIRTH_DATE));
    }

    @Test
    @DisplayName("Sin identificador de mensaje, el servicio recibe null y es él quien lo rechaza")
    void onAccountCreated_shouldPassNullMessageId_whenHeaderIsMissing() {
        listener.onAccountCreated(new AccountCreatedPayloadV1(USER_ID, BIRTH_DATE), null);

        var command = ArgumentCaptor.forClass(ReplicateBirthDateCommand.class);
        verify(service).recordAccountCreated(command.capture());
        assertThat(command.getValue().messageId()).isNull();
    }

    @Test
    @DisplayName("Un evento que incumple el contrato se rechaza sin reencolar, con su razón y un solo registro")
    void onAccountCreated_shouldRejectWithoutRequeue_whenEventIsInvalid() {
        doThrow(new InvalidAccountEventException(Reason.USER_ID_INVALID)).when(service).recordAccountCreated(any());

        assertThatThrownBy(() -> listener.onAccountCreated(new AccountCreatedPayloadV1(" ", BIRTH_DATE), MESSAGE_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("USER_ID_INVALID");

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("cola=perfil.cuenta-creada", "messageId=" + MESSAGE_ID,
                    "razón=USER_ID_INVALID");
        });
    }

    @Test
    @DisplayName("Un cuerpo vacío se rechaza como formato inválido sin llegar al servicio")
    void onAccountCreated_shouldRejectWithoutRequeue_whenPayloadIsNull() {
        assertThatThrownBy(() -> listener.onAccountCreated(null, MESSAGE_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Un fallo técnico se propaga tal cual para que el contenedor lo reintente")
    void onAccountCreated_shouldPropagateTechnicalFailure_whenDatabaseFails() {
        var failure = new DataAccessResourceFailureException("base de datos caída");
        doThrow(failure).when(service).recordAccountCreated(any());

        assertThatThrownBy(() -> listener.onAccountCreated(new AccountCreatedPayloadV1(USER_ID, BIRTH_DATE), MESSAGE_ID))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("Al rechazar un evento no se registra la fecha de nacimiento ni la identidad")
    void onAccountCreated_shouldNotLogPayload_whenRejected() {
        doThrow(new InvalidAccountEventException(Reason.BIRTH_DATE_IN_THE_FUTURE)).when(service).recordAccountCreated(any());

        assertThatThrownBy(() -> listener.onAccountCreated(new AccountCreatedPayloadV1(USER_ID, BIRTH_DATE), MESSAGE_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(logs.list).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain("2008-03-15").doesNotContain(USER_ID));
    }

    @Test
    @DisplayName("Un identificador con saltos de línea no se copia al registro")
    void onAccountCreated_shouldLogInvalidMessageId_whenIdHasLineBreaks() {
        doThrow(new InvalidAccountEventException(Reason.MESSAGE_ID_INVALID)).when(service).recordAccountCreated(any());

        assertThatThrownBy(() -> listener.onAccountCreated(new AccountCreatedPayloadV1(USER_ID, BIRTH_DATE), "a\nb"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(logs.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("messageId=invalid").doesNotContain("\n"));
    }
}
