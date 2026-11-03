package co.edu.unicauca.cameia.perfil.infrastructure.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de los eventos de cuenta que consume este servicio.
 *
 * <p>Las dos colas envían sus mensajes fallidos a {@code perfil.dlx} con su propio nombre como clave de enrutamiento, así que cada
 * mensaje fallido llega a la cola de fallidos de su origen. El exchange de cuentas también se declara aquí, con los mismos
 * atributos que usa el productor, para que el orden de arranque no importe.</p>
 */
@Configuration
public class AccountEventsRabbitConfig {

    /** Exchange donde Cuentas publica sus eventos. */
    public static final String ACCOUNTS_EXCHANGE = "cuentas.events";
    /** Exchange al que van los mensajes que una cola rechaza sin reencolar. */
    public static final String DEAD_LETTER_EXCHANGE = "perfil.dlx";
    /** Cola de {@code cuenta.creada}. */
    public static final String ACCOUNT_CREATED_QUEUE = "perfil.cuenta-creada";
    /** Cola de {@code cuenta.eliminada}. */
    public static final String ACCOUNT_DELETED_QUEUE = "perfil.cuenta-eliminada";

    static final String ACCOUNT_CREATED_KEY = "cuenta.creada";
    static final String ACCOUNT_DELETED_KEY = "cuenta.eliminada";
    static final String DEAD_LETTER_SUFFIX = ".dlq";

    /** @return exchange de eventos de cuenta, con los atributos del productor */
    @Bean
    TopicExchange accountsExchange() {
        return new TopicExchange(ACCOUNTS_EXCHANGE, true, false);
    }

    /** @return exchange de mensajes fallidos */
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /** @return cola de cuentas creadas, cuyos rechazos van a su cola de fallidos */
    @Bean
    Queue accountCreatedQueue() {
        return withDeadLetter(ACCOUNT_CREATED_QUEUE);
    }

    /** @return cola de cuentas eliminadas, cuyos rechazos van a su cola de fallidos */
    @Bean
    Queue accountDeletedQueue() {
        return withDeadLetter(ACCOUNT_DELETED_QUEUE);
    }

    /** @return cola de fallidos de las cuentas creadas */
    @Bean
    Queue accountCreatedDeadLetterQueue() {
        return QueueBuilder.durable(ACCOUNT_CREATED_QUEUE + DEAD_LETTER_SUFFIX).build();
    }

    /** @return cola de fallidos de las cuentas eliminadas */
    @Bean
    Queue accountDeletedDeadLetterQueue() {
        return QueueBuilder.durable(ACCOUNT_DELETED_QUEUE + DEAD_LETTER_SUFFIX).build();
    }

    /** @return enlace de {@code cuenta.creada} a su cola */
    @Bean
    Binding accountCreatedBinding(TopicExchange accountsExchange, Queue accountCreatedQueue) {
        return BindingBuilder.bind(accountCreatedQueue).to(accountsExchange).with(ACCOUNT_CREATED_KEY);
    }

    /** @return enlace de {@code cuenta.eliminada} a su cola */
    @Bean
    Binding accountDeletedBinding(TopicExchange accountsExchange, Queue accountDeletedQueue) {
        return BindingBuilder.bind(accountDeletedQueue).to(accountsExchange).with(ACCOUNT_DELETED_KEY);
    }

    /** @return enlace de la cola de fallidos de cuentas creadas, con el nombre de su cola de origen como clave */
    @Bean
    Binding accountCreatedDeadLetterBinding(DirectExchange deadLetterExchange, Queue accountCreatedDeadLetterQueue) {
        return BindingBuilder.bind(accountCreatedDeadLetterQueue).to(deadLetterExchange).with(ACCOUNT_CREATED_QUEUE);
    }

    /** @return enlace de la cola de fallidos de cuentas eliminadas, con el nombre de su cola de origen como clave */
    @Bean
    Binding accountDeletedDeadLetterBinding(DirectExchange deadLetterExchange, Queue accountDeletedDeadLetterQueue) {
        return BindingBuilder.bind(accountDeletedDeadLetterQueue).to(deadLetterExchange).with(ACCOUNT_DELETED_QUEUE);
    }

    private static Queue withDeadLetter(String name) {
        return QueueBuilder.durable(name).deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(name).build();
    }
}
