package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Deja fuera del reintento los mensajes que incumplen el contrato: un dato inválido no mejora al repetirse. Sin esto Spring
 * reintenta todo y cada mensaje inválido haría tres intentos y dejaría tres registros.
 */
@Configuration
public class AccountEventsRetryConfig {

    /** @return el personalizador que excluye del reintento los rechazos por contrato */
    @Bean
    RabbitListenerRetrySettingsCustomizer accountEventsRetryCustomizer() {
        return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
    }
}
