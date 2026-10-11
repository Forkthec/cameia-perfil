package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Declara el exchange, las colas y los bindings de cameia-perfil.
 *
 * Nombres CONFIRMADOS en el C2 del equipo (kebab-case, hecho pasado):
 *   - suscripcion-actualizada         → produce Cuentas, consume aquí
 *   - consumo-registrado              → produce Auditoría, consume aquí
 *
 * Los eventos que produce Perfil salen por el exchange {@value #EXCHANGE_PERFIL}, con el tipo del evento como clave de
 * enrutamiento; la cola que los recibe la declara cada consumidor, no Perfil. Las colas de los eventos de cuenta viven
 * en {@link AccountEventsRabbitConfig}.
 * TODO CM-XXX: configurar DLQ, prefetch, retry y TTL de las colas restantes cuando se implemente su consumidor real.
 * TODO CM-XXX: externalizar nombres de colas a application.yml con prefijo cameia.rabbit.*
 */
@Configuration
public class RabbitConfig {

    // ── Nombres de colas (CONFIRMADO C2) ────────────────────────────────────
    public static final String QUEUE_SUSCRIPCION_ACT      = "suscripcion-actualizada";
    public static final String QUEUE_CONSUMO_REGISTRADO   = "consumo-registrado";

    // ── Exchange principal de CAMEIA (PROPUESTO — confirmar con el equipo) ──
    public static final String EXCHANGE_CAMEIA = "cameia.events";

    /** Exchange de los eventos que produce Perfil. */
    public static final String EXCHANGE_PERFIL = "perfil.events";

    /** Exchange de tipo tema, durable y sin borrado automático, donde Perfil publica cada evento con su tipo como clave. */
    @Bean
    TopicExchange perfilExchange() {
        return new TopicExchange(EXCHANGE_PERFIL, true, false);
    }

    /**
     * Acota el saludo inicial del protocolo AMQP al mismo plazo que la conexión.
     *
     * <p>Sin este ajuste, un broker que acepta la conexión TCP pero no responde retiene la publicación los 10 segundos que
     * el cliente usa por defecto, y la petición no puede esperar tanto (DES-02).</p>
     *
     * @param properties propiedades {@code spring.rabbitmq.*} ya enlazadas
     * @return el ajuste de la fábrica de conexiones
     */
    @Bean
    ConnectionFactoryCustomizer handshakeTimeoutCustomizer(RabbitProperties properties) {
        return factory -> factory.setHandshakeTimeout((int) properties.getConnectionTimeout().toMillis());
    }

    /**
     * Pide al broker que devuelva un mensaje sin cola de destino y evita el aviso duplicado de la plantilla.
     *
     * <p>La devolución ya se trata en el publicador, que la cuenta como no publicada y la registra una vez.</p>
     *
     * @return el ajuste de la plantilla
     */
    @Bean
    RabbitTemplateCustomizer publisherTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);
            template.setReturnsCallback(returned -> { });
        };
    }

    @Bean
    TopicExchange cameiaExchange() {
        return new TopicExchange(EXCHANGE_CAMEIA, true, false);
    }

    // ── Colas ────────────────────────────────────────────────────────────────

    @Bean Queue queueSuscripcionAct()    { return new Queue(QUEUE_SUSCRIPCION_ACT,    true); }
    @Bean Queue queueConsumoRegistrado() { return new Queue(QUEUE_CONSUMO_REGISTRADO, true); }

    // ── Bindings (routing key = nombre de la cola) ───────────────────────────

    @Bean Binding bindingSuscripcionAct(TopicExchange cameiaExchange, Queue queueSuscripcionAct) {
        return BindingBuilder.bind(queueSuscripcionAct).to(cameiaExchange).with(QUEUE_SUSCRIPCION_ACT);
    }
    @Bean Binding bindingConsumoRegistrado(TopicExchange cameiaExchange, Queue queueConsumoRegistrado) {
        return BindingBuilder.bind(queueConsumoRegistrado).to(cameiaExchange).with(QUEUE_CONSUMO_REGISTRADO);
    }

    // ── Serialización JSON ───────────────────────────────────────────────────
    @Bean
    JacksonJsonMessageConverter messageConverter() {
        var converter = new JacksonJsonMessageConverter();
        // Se usa el tipo del parámetro del consumidor y se ignora el encabezado __TypeId__: un mensaje no debe elegir qué
        // clase se instancia.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
