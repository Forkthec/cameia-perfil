package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.domain.exception.RedactedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

/**
 * Recibe el mensaje cuando se agotan los intentos y lo manda a la cola de fallidos de su origen.
 *
 * <p>Es el único sitio que registra en {@code ERROR}, con traza, un fallo técnico: una sola línea por mensaje, con la cola, el
 * identificador y la clase del fallo, y nunca con el cuerpo. La traza va sin los mensajes de las excepciones, porque el de una
 * excepción de persistencia puede traer los valores de las columnas. Un mensaje que incumple el contrato también pasa por aquí
 * (el reintento lo excluye, pero no el recuperador): ya quedó registrado una vez en {@code WARN}, así que solo se rechaza.
 * Rechaza sin reencolar, como lo hace el recuperador por defecto de Spring, para que el broker lo enrute por la configuración
 * de mensajes fallidos de la cola.</p>
 */
@Component
public class AccountEventsRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventsRecoverer.class);

    /**
     * Registra el mensaje agotado y lo rechaza sin reencolar.
     *
     * @param message mensaje original del broker
     * @param cause   último fallo del consumidor
     * @throws ListenerExecutionFailedException siempre, con una causa de rechazo sin reencolar
     */
    @Override
    public void recover(Message message, Throwable cause) {
        if (!isContractRejection(cause)) {
            var properties = message.getMessageProperties();
            log.error("Mensaje de cuenta agotado tras los reintentos; va a la cola de fallidos [cola={}, messageId={}, causa={}]",
                    properties.getConsumerQueue(), EventLogValues.safeMessageId(properties.getMessageId()),
                    AccountEventsErrorHandler.rootCauseName(cause), RedactedException.of(cause));
        }
        throw new ListenerExecutionFailedException("Intentos agotados", new AmqpRejectAndDontRequeueException(cause), message);
    }

    /** Un rechazo por contrato ya se registró al detectarlo; solo un fallo técnico se registra aquí. */
    private static boolean isContractRejection(Throwable failure) {
        return AccountEventsErrorHandler.hasCause(failure, AmqpRejectAndDontRequeueException.class);
    }
}
