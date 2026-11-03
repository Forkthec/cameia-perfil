package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Las colas de Entrevista que declara Perfil llevan los argumentos exactos que Entrevista usa al declararlas. */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    @DisplayName("La cola del catálogo de roles es durable y sus rechazos van al exchange de fallidos con su propio nombre")
    void interviewRoleCatalogQueue_shouldHaveExactArguments_whenDeclared() {
        Queue queue = config.interviewRoleCatalogQueue();

        assertThat(queue.getName()).isEqualTo("entrevista.catalogo-roles-actualizado");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments()).isEqualTo(Map.of(
                "x-dead-letter-exchange", "entrevista.dlx",
                "x-dead-letter-routing-key", "entrevista.catalogo-roles-actualizado"));
    }

    @Test
    @DisplayName("La cola del catálogo queda ligada a perfil.events con la clave perfil.catalogo-roles-actualizado")
    void interviewRoleCatalogBinding_shouldUseTheEventTypeAsKey_whenDeclared() {
        TopicExchange exchange = config.perfilExchange();
        Binding binding = config.interviewRoleCatalogBinding(exchange, config.interviewRoleCatalogQueue());

        assertThat(binding.getExchange()).isEqualTo("perfil.events");
        assertThat(binding.getDestination()).isEqualTo("entrevista.catalogo-roles-actualizado");
        assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
        assertThat(binding.getRoutingKey()).isEqualTo("perfil.catalogo-roles-actualizado");
        assertThat(RabbitConfig.ROLE_CATALOG_KEY).isEqualTo("perfil.catalogo-roles-actualizado");
    }

    @Test
    @DisplayName("La cola del perfil conserva sus argumentos")
    void interviewProfileQueue_shouldKeepItsArguments_whenDeclared() {
        Queue queue = config.interviewProfileQueue();

        assertThat(queue.getName()).isEqualTo("entrevista.perfil-profesional-actualizado");
        assertThat(queue.getArguments()).isEqualTo(Map.of(
                "x-dead-letter-exchange", "entrevista.dlx",
                "x-dead-letter-routing-key", "entrevista.perfil-profesional-actualizado"));
    }
}
