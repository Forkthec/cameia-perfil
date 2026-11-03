package co.edu.unicauca.cameia.perfil.infrastructure.config;

import co.edu.unicauca.cameia.perfil.application.service.OutboxRelayAppService;
import co.edu.unicauca.cameia.perfil.domain.event.RelaySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Con el perfil {@code profile-events-relay} el servicio es una tarea: relaya una vez, sin puerto HTTP y sin consumidores. */
@SpringBootTest
@ActiveProfiles(ProfileEventsRelayJobRunner.PROFILE)
@Testcontainers
class ProfileEventsRelayJobRunnerTest {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Relevo de mentira que cuenta sus llamadas y devuelve un resumen fijo. */
    @TestConfiguration
    static class RelayDouble {
        @Bean
        @Primary
        OutboxRelayAppService relay() {
            var relay = Mockito.mock(OutboxRelayAppService.class);
            Mockito.when(relay.relayPending()).thenReturn(new RelaySummary(3, 1, 2));
            return relay;
        }
    }

    @Autowired
    OutboxRelayAppService relay;
    @Autowired
    ApplicationContext context;
    @Autowired
    RabbitListenerEndpointRegistry listeners;

    @Test
    @DisplayName("Al arrancar con el perfil de relevo se publican los pendientes una vez")
    void run_shouldRelayPending_whenProfileIsActive() {
        verify(relay).relayPending();
    }

    @Test
    @DisplayName("El contexto de la tarea no abre un servidor web")
    void context_shouldHaveNoWebServer_whenProfileIsActive() {
        assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
    }

    @Test
    @DisplayName("Ningún consumidor de Perfil arranca en la tarea")
    void listeners_shouldNotStart_whenProfileIsActive() {
        assertThat(listeners.getListenerContainers()).isNotEmpty().noneMatch(container -> container.isRunning());
    }
}
