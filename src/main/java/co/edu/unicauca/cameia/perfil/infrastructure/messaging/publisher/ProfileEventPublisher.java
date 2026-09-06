package co.edu.unicauca.cameia.perfil.infrastructure.messaging.publisher;

import co.edu.unicauca.cameia.perfil.infrastructure.messaging.config.RabbitConfig;
import co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica el evento perfil-profesional-actualizado (CONFIRMADO C2).
 *
 * TODO CM-XXX: implementar patrón Outbox — el evento y el cambio de negocio
 *   deben persistirse en la misma transacción local antes de publicar
 *   (reglas de código §13, glosario §5 "Outbox").
 * TODO CM-XXX: llamar desde ProfileAppService después de cada operación que
 *   modifique el perfil (updateProfileInfo, addWorkExperience, etc.).
 */
@Component
public class ProfileEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProfileEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ProfileEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishProfileUpdated(ProfileUpdatedPayloadV1 payload) {
        log.info("publicando perfil-profesional-actualizado profileId={}", payload.profileId());
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CAMEIA,
                RabbitConfig.QUEUE_PERFIL_ACTUALIZADO,
                payload
        );
    }
}
