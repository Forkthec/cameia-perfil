package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.application.command.RemoveBirthDateCommand;
import co.edu.unicauca.cameia.perfil.application.service.BirthDateReplicaAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.AccountEventsRabbitConfig;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.AccountDeletedPayloadV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consume {@code cuenta.eliminada} y borra la fecha de nacimiento del Usuario de la réplica local.
 *
 * <p>Tiene la misma forma que {@link AccountCreatedListener}: el contenido que incumple el contrato va a la cola de fallidos sin
 * reintentos, los fallos técnicos se propagan para que el contenedor los reintente y el cuerpo nunca se escribe en el log.</p>
 */
@Component
public class AccountDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletedListener.class);

    private final BirthDateReplicaAppService service;

    AccountDeletedListener(BirthDateReplicaAppService service) {
        this.service = service;
    }

    /**
     * Aplica un evento de cuenta eliminada.
     *
     * @param payload   campos del contrato que este servicio lee; {@code null} si el cuerpo estaba vacío
     * @param messageId identificador del mensaje puesto por el productor; puede faltar
     * @throws AmqpRejectAndDontRequeueException si el evento incumple el contrato: va a la cola de fallidos sin reintentos
     */
    @RabbitListener(queues = AccountEventsRabbitConfig.ACCOUNT_DELETED_QUEUE, errorHandler = "accountEventsErrorHandler")
    public void onAccountDeleted(AccountDeletedPayloadV1 payload,
                                 @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (payload == null) {
            throw reject(messageId, "PAYLOAD_INVALID_FORMAT");
        }
        try {
            service.recordAccountDeleted(new RemoveBirthDateCommand(messageId, payload.userId()));
        } catch (InvalidAccountEventException invalid) {
            throw reject(messageId, invalid.getReason().name());
        }
    }

    private static AmqpRejectAndDontRequeueException reject(String messageId, String reason) {
        log.warn("Evento de cuenta rechazado hacia la cola de fallidos [cola={}, messageId={}, razón={}]",
                AccountEventsRabbitConfig.ACCOUNT_DELETED_QUEUE, EventLogValues.safeMessageId(messageId), reason);
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
