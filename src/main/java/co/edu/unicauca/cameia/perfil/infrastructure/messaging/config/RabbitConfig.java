package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara el exchange, las colas y los bindings de cameia-perfil.
 *
 * Nombres CONFIRMADOS en el C2 del equipo (kebab-case, hecho pasado):
 *   - perfil-profesional-actualizado  → produce este microservicio
 *   - cuenta-eliminada                → produce Cuentas, consume aquí
 *   - suscripcion-actualizada         → produce Cuentas, consume aquí
 *   - consumo-registrado              → produce Auditoría, consume aquí
 *
 * TODO CM-XXX: configurar DLQ, prefetch, retry y TTL cuando se implemente el consumidor real.
 * TODO CM-XXX: externalizar nombres de colas a application.yml con prefijo cameia.rabbit.*
 */
@Configuration
public class RabbitConfig {

    // ── Nombres de colas (CONFIRMADO C2) ────────────────────────────────────
    public static final String QUEUE_PERFIL_ACTUALIZADO   = "perfil-profesional-actualizado";
    public static final String QUEUE_CUENTA_ELIMINADA     = "cuenta-eliminada";
    public static final String QUEUE_SUSCRIPCION_ACT      = "suscripcion-actualizada";
    public static final String QUEUE_CONSUMO_REGISTRADO   = "consumo-registrado";

    // ── Exchange principal de CAMEIA (PROPUESTO — confirmar con el equipo) ──
    public static final String EXCHANGE_CAMEIA = "cameia.events";

    @Bean
    TopicExchange cameiaExchange() {
        return new TopicExchange(EXCHANGE_CAMEIA, true, false);
    }

    // ── Colas ────────────────────────────────────────────────────────────────

    @Bean Queue queuePerfilActualizado() { return new Queue(QUEUE_PERFIL_ACTUALIZADO, true); }
    @Bean Queue queueCuentaEliminada()   { return new Queue(QUEUE_CUENTA_ELIMINADA,   true); }
    @Bean Queue queueSuscripcionAct()    { return new Queue(QUEUE_SUSCRIPCION_ACT,    true); }
    @Bean Queue queueConsumoRegistrado() { return new Queue(QUEUE_CONSUMO_REGISTRADO, true); }

    // ── Bindings (routing key = nombre de la cola) ───────────────────────────

    @Bean Binding bindingPerfilActualizado(TopicExchange cameiaExchange, Queue queuePerfilActualizado) {
        return BindingBuilder.bind(queuePerfilActualizado).to(cameiaExchange).with(QUEUE_PERFIL_ACTUALIZADO);
    }
    @Bean Binding bindingCuentaEliminada(TopicExchange cameiaExchange, Queue queueCuentaEliminada) {
        return BindingBuilder.bind(queueCuentaEliminada).to(cameiaExchange).with(QUEUE_CUENTA_ELIMINADA);
    }
    @Bean Binding bindingSuscripcionAct(TopicExchange cameiaExchange, Queue queueSuscripcionAct) {
        return BindingBuilder.bind(queueSuscripcionAct).to(cameiaExchange).with(QUEUE_SUSCRIPCION_ACT);
    }
    @Bean Binding bindingConsumoRegistrado(TopicExchange cameiaExchange, Queue queueConsumoRegistrado) {
        return BindingBuilder.bind(queueConsumoRegistrado).to(cameiaExchange).with(QUEUE_CONSUMO_REGISTRADO);
    }

    // ── Serialización JSON ───────────────────────────────────────────────────
    @Bean
    JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
