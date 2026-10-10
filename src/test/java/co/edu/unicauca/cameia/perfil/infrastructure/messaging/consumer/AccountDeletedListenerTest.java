package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.application.command.RemoveBirthDateCommand;
import co.edu.unicauca.cameia.perfil.application.service.BirthDateReplicaAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException.Reason;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.AccountDeletedPayloadV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El consumidor de {@code cuenta.eliminada} traduce el mensaje en un comando y clasifica sus fallos. */
class AccountDeletedListenerTest {

    private static final String MESSAGE_ID = "22222222-2222-4222-8222-222222222222";
    private static final String USER_ID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";

    private final BirthDateReplicaAppService service = mock(BirthDateReplicaAppService.class);
    private final AccountDeletedListener listener = new AccountDeletedListener(service);

    @Test
    @DisplayName("Un evento válido llega al servicio con el identificador del mensaje y la identidad")
    void onAccountDeleted_shouldCallService_whenPayloadIsValid() {
        listener.onAccountDeleted(new AccountDeletedPayloadV1(USER_ID), MESSAGE_ID);

        var command = ArgumentCaptor.forClass(RemoveBirthDateCommand.class);
        verify(service).recordAccountDeleted(command.capture());
        assertThat(command.getValue()).isEqualTo(new RemoveBirthDateCommand(MESSAGE_ID, USER_ID));
    }

    @Test
    @DisplayName("Un evento que incumple el contrato se rechaza sin reencolar y con su razón")
    void onAccountDeleted_shouldRejectWithoutRequeue_whenEventIsInvalid() {
        doThrow(new InvalidAccountEventException(Reason.USER_ID_INVALID)).when(service).recordAccountDeleted(any());

        assertThatThrownBy(() -> listener.onAccountDeleted(new AccountDeletedPayloadV1("  "), MESSAGE_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("USER_ID_INVALID");
    }

    @Test
    @DisplayName("Un cuerpo vacío se rechaza como formato inválido sin llegar al servicio")
    void onAccountDeleted_shouldRejectWithoutRequeue_whenPayloadIsNull() {
        assertThatThrownBy(() -> listener.onAccountDeleted(null, MESSAGE_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Un fallo técnico se propaga tal cual para que el contenedor lo reintente")
    void onAccountDeleted_shouldPropagateTechnicalFailure_whenDatabaseFails() {
        var failure = new DataAccessResourceFailureException("base de datos caída");
        doThrow(failure).when(service).recordAccountDeleted(any());

        assertThatThrownBy(() -> listener.onAccountDeleted(new AccountDeletedPayloadV1(USER_ID), MESSAGE_ID))
                .isSameAs(failure);
    }
}
