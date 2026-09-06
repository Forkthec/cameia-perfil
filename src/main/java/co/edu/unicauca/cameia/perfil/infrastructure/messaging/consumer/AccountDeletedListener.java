package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha el evento cuenta-eliminada producido por el microservicio Cuentas (CONFIRMADO C2).
 *
 * Cuando una cuenta se elimina, este contexto debe eliminar o anonimizar el perfil asociado.
 *
 * TODO CM-XXX: definir el payload que envía Cuentas (coordinar con el equipo de Cuentas).
 * TODO CM-XXX: implementar idempotencia — verificar idMensaje contra tabla de mensajes
 *   procesados antes de aplicar (reglas §13, glosario "Inbox").
 * TODO CM-XXX: llamar al servicio de aplicación para eliminar/anonimizar el perfil.
 */
@Component
public class AccountDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletedListener.class);

    @RabbitListener(queues = RabbitConfig.QUEUE_CUENTA_ELIMINADA)
    public void onAccountDeleted(Object payload) {
        // TODO CM-XXX: deserializar payload tipado y eliminar perfil asociado
        log.warn("cuenta-eliminada recibido — implementación pendiente CM-XXX payload={}", payload);
    }
}
