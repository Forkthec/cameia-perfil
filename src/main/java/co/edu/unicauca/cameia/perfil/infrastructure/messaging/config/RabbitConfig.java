package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
 * enrutamiento; la cola de Entrevista se declara también aquí, con los argumentos de Entrevista, para que ningún cambio
 * quede sin destino. Las colas de los eventos de cuenta viven en {@link AccountEventsRabbitConfig}.
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

    /** Clave de enrutamiento de los cambios del Perfil Profesional activo. */
    public static final String PROFILE_UPDATED_KEY = ProfileUpdated.TYPE;

    /** Cola donde Entrevista recibe los cambios del perfil. */
    public static final String INTERVIEW_PROFILE_QUEUE = "entrevista.perfil-profesional-actualizado";

    /** Clave de enrutamiento de la instantánea del catálogo de roles. */
    public static final String ROLE_CATALOG_KEY = RoleCatalogUpdated.TYPE;

    /** Cola donde Entrevista recibe la instantánea del catálogo de roles. */
    public static final String INTERVIEW_ROLE_CATALOG_QUEUE = "entrevista.catalogo-roles-actualizado";

    /** Exchange de fallidos de Entrevista; lo declara Entrevista junto con su cola de fallidos. */
    public static final String INTERVIEW_DEAD_LETTER_EXCHANGE = "entrevista.dlx";

    /** Exchange de tipo tema, durable y sin borrado automático, donde Perfil publica cada evento con su tipo como clave. */
    @Bean
    TopicExchange perfilExchange() {
        return new TopicExchange(EXCHANGE_PERFIL, true, false);
    }

    /**
     * Cola de Entrevista para los cambios del perfil, declarada también aquí con los mismos argumentos que usa Entrevista.
     *
     * <p>Así ningún cambio se pierde ni queda devuelto mientras Entrevista no esté desplegada: los mensajes esperan en la
     * cola. Si los argumentos no coinciden, el broker rechaza la declaración de quien llegue segundo.</p>
     *
     * @return cola durable cuyos rechazos van a {@value #INTERVIEW_DEAD_LETTER_EXCHANGE}
     */
    @Bean
    Queue interviewProfileQueue() {
        return QueueBuilder.durable(INTERVIEW_PROFILE_QUEUE).deadLetterExchange(INTERVIEW_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(INTERVIEW_PROFILE_QUEUE).build();
    }

    /**
     * Liga los cambios del perfil a la cola de Entrevista.
     *
     * @param perfilExchange        exchange de los eventos de Perfil
     * @param interviewProfileQueue cola de Entrevista
     * @return el enlace con la clave {@value #PROFILE_UPDATED_KEY}
     */
    @Bean
    Binding interviewProfileBinding(TopicExchange perfilExchange, Queue interviewProfileQueue) {
        return BindingBuilder.bind(interviewProfileQueue).to(perfilExchange).with(PROFILE_UPDATED_KEY);
    }

    /**
     * Cola de Entrevista para la instantánea del catálogo de roles, declarada también aquí con los mismos argumentos que
     * usa Entrevista.
     *
     * <p>Sin la cola el broker devolvería el mensaje (el publicador usa {@code mandatory}) y el evento quedaría pendiente
     * para siempre; con ella, la instantánea espera hasta que Entrevista esté desplegada.</p>
     *
     * @return cola durable cuyos rechazos van a {@value #INTERVIEW_DEAD_LETTER_EXCHANGE}
     */
    @Bean
    Queue interviewRoleCatalogQueue() {
        return QueueBuilder.durable(INTERVIEW_ROLE_CATALOG_QUEUE).deadLetterExchange(INTERVIEW_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(INTERVIEW_ROLE_CATALOG_QUEUE).build();
    }

    /**
     * Liga la instantánea del catálogo a la cola de Entrevista.
     *
     * @param perfilExchange            exchange de los eventos de Perfil
     * @param interviewRoleCatalogQueue cola de Entrevista para el catálogo
     * @return el enlace con la clave {@value #ROLE_CATALOG_KEY}
     */
    @Bean
    Binding interviewRoleCatalogBinding(TopicExchange perfilExchange, Queue interviewRoleCatalogQueue) {
        return BindingBuilder.bind(interviewRoleCatalogQueue).to(perfilExchange).with(ROLE_CATALOG_KEY);
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
