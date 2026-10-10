package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.application.command.ReplicateBirthDateCommand;
import co.edu.unicauca.cameia.perfil.application.service.BirthDateReplicaAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.AccountEventsRabbitConfig;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.AccountCreatedPayloadV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consume {@code cuenta.creada} y guarda la fecha de nacimiento del Usuario en la réplica local.
 *
 * <p>El contenido que incumple el contrato se rechaza sin reencolar y termina en la cola de fallidos; los fallos técnicos se
 * propagan para que el contenedor los reintente. El cuerpo del mensaje nunca se escribe en el log.</p>
 */
@Component
public class AccountCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCreatedListener.class);

    private final BirthDateReplicaAppService service;

    AccountCreatedListener(BirthDateReplicaAppService service) {
        this.service = service;
    }

    /**
     * Aplica un evento de cuenta creada.
     *
     * @param payload   campos del contrato que este servicio lee; {@code null} si el cuerpo estaba vacío
     * @param messageId identificador del mensaje puesto por el productor; puede faltar
     * @throws AmqpRejectAndDontRequeueException si el evento incumple el contrato: va a la cola de fallidos sin reintentos
     */
    @RabbitListener(queues = AccountEventsRabbitConfig.ACCOUNT_CREATED_QUEUE, errorHandler = "accountEventsErrorHandler")
    public void onAccountCreated(AccountCreatedPayloadV1 payload,
                                 @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (payload == null) {
            throw reject(messageId, "PAYLOAD_INVALID_FORMAT");
        }
        try {
            service.recordAccountCreated(new ReplicateBirthDateCommand(messageId, payload.userId(), payload.birthDate()));
        } catch (InvalidAccountEventException invalid) {
            throw reject(messageId, invalid.getReason().name());
        }
    }

    private static AmqpRejectAndDontRequeueException reject(String messageId, String reason) {
        log.warn("Evento de cuenta rechazado hacia la cola de fallidos [cola={}, messageId={}, razón={}]",
                AccountEventsRabbitConfig.ACCOUNT_CREATED_QUEUE, EventLogValues.safeMessageId(messageId), reason);
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
