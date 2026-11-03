package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.retry.RetryPolicySettings;

import static org.assertj.core.api.Assertions.assertThat;

/** Los rechazos por contrato quedan fuera del reintento. */
class AccountEventsRetryConfigTest {

    @Test
    @DisplayName("El personalizador excluye del reintento el rechazo sin reencolar")
    void customize_shouldExcludeRejectAndDontRequeue_whenSettingsAreApplied() {
        var settings = new RetryPolicySettings();

        new AccountEventsRetryConfig().accountEventsRetryCustomizer().customize(settings);

        assertThat(settings.getExceptionExcludes()).containsExactly(AmqpRejectAndDontRequeueException.class);
    }
}
