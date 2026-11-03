package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha el evento suscripcion-actualizada producido por Cuentas (CONFIRMADO C2).
 *
 * Cuando la suscripción cambia, este contexto puede necesitar actualizar límites
 * (ej: máximo de roles objetivo, habilidades, etc.).
 *
 * TODO CM-XXX: definir qué campos del plan afectan a Perfil (coordinar con Cuentas).
 * TODO CM-XXX: implementar idempotencia (Inbox pattern, reglas §13).
 * TODO CM-XXX: actualizar límites del perfil según el nuevo plan de suscripción.
 */
@Component
public class SubscriptionUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionUpdatedListener.class);

    @RabbitListener(queues = RabbitConfig.QUEUE_SUSCRIPCION_ACT)
    public void onSubscriptionUpdated(Object payload) {
        // TODO CM-XXX: deserializar payload tipado y actualizar límites del perfil
        log.warn("suscripcion-actualizada recibido — implementación pendiente CM-XXX payload={}", payload);
    }
}
