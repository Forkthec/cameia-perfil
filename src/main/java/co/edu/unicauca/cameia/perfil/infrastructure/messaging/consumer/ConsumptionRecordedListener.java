package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha el evento consumo-registrado producido por Auditoría (CONFIRMADO C2).
 *
 * Permite que Perfil actualice contadores locales o proyecciones sin consultar
 * directamente a Auditoría (consistencia eventual, glosario §5).
 *
 * TODO CM-XXX: definir si Perfil realmente necesita reaccionar a este evento
 *   o si es solo para Cuentas (confirmar context map con el equipo).
 * TODO CM-XXX: implementar idempotencia (Inbox pattern, reglas §13).
 */
@Component
public class ConsumptionRecordedListener {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionRecordedListener.class);

    @RabbitListener(queues = RabbitConfig.QUEUE_CONSUMO_REGISTRADO)
    public void onConsumptionRecorded(Object payload) {
        // TODO CM-XXX: verificar con el equipo si Perfil consume este evento
        log.warn("consumo-registrado recibido — implementación pendiente CM-XXX payload={}", payload);
    }
}
