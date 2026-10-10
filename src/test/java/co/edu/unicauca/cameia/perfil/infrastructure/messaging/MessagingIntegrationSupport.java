package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * Base de las pruebas de integración de los eventos de cuenta: un PostgreSQL y un RabbitMQ reales, arrancados una sola vez
 * para todas las clases que la extienden.
 *
 * <p>Los contenedores se arrancan en el inicializador estático (y no con {@code @Container}) para que el contexto de Spring
 * que se reutiliza entre clases nunca apunte a un contenedor ya detenido; Testcontainers los retira al terminar la JVM.</p>
 */
@SpringBootTest
abstract class MessagingIntegrationSupport {

    static final String ACCOUNTS_EXCHANGE = "cuentas.events";
    static final String CREATED_KEY = "cuenta.creada";
    static final String DELETED_KEY = "cuenta.eliminada";
    static final String CREATED_QUEUE = "perfil.cuenta-creada";
    static final String DELETED_QUEUE = "perfil.cuenta-eliminada";
    static final String CREATED_DLQ = CREATED_QUEUE + ".dlq";
    static final String DELETED_DLQ = DELETED_QUEUE + ".dlq";

    static final String USER_ID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    static final String BIRTH_DATE = "2008-03-15";
    static final String EMAIL = "ana.perez@ejemplo.test";

    /** Cuerpo literal del contrato publicado por Cuentas. */
    static final String CREATED_BODY = "{\"usuarioId\":\"" + USER_ID + "\",\"email\":\"" + EMAIL
            + "\",\"fechaNacimiento\":\"" + BIRTH_DATE + "\",\"creadaEn\":\"2026-10-09T15:04:05.123Z\"}";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final GenericContainer<?> RABBIT = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** Deja la base y las cuatro colas vacías antes de cada prueba. */
    void resetState() {
        jdbcTemplate.update("delete from evento_procesado");
        jdbcTemplate.update("delete from fecha_nacimiento_usuario");
        for (var queue : new String[] {CREATED_QUEUE, DELETED_QUEUE, CREATED_DLQ, DELETED_DLQ}) {
            rabbitAdmin.purgeQueue(queue, false);
        }
    }

    /** Publica un cuerpo con las propiedades de un productor real; sin {@code messageId} si llega {@code null}. */
    void publish(String routingKey, String body, String messageId) {
        publish(routingKey, body.getBytes(StandardCharsets.UTF_8), messageId, null);
    }

    /** Publica bytes crudos, para cuerpos que no son texto válido, con un encabezado {@code __TypeId__} opcional. */
    void publish(String routingKey, byte[] body, String messageId, String typeId) {
        var builder = MessageBuilder.withBody(body).setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (messageId != null) {
            builder.setMessageId(messageId);
        }
        if (typeId != null) {
            builder.setHeader("__TypeId__", typeId);
        }
        Message message = builder.build();
        rabbitTemplate.send(ACCOUNTS_EXCHANGE, routingKey, message);
    }

    long messageCount(String queue) {
        var info = rabbitAdmin.getQueueInfo(queue);
        return info == null ? -1 : info.getMessageCount();
    }

    int replicaRows() {
        return jdbcTemplate.queryForObject("select count(*) from fecha_nacimiento_usuario", Integer.class);
    }

    int inboxRows() {
        return jdbcTemplate.queryForObject("select count(*) from evento_procesado", Integer.class);
    }

    /** Espera a que la cola de fallidos tenga el mensaje y a que la principal quede vacía. */
    void awaitDeadLettered(String mainQueue, String deadLetterQueue) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            org.assertj.core.api.Assertions.assertThat(messageCount(deadLetterQueue)).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(messageCount(mainQueue)).isZero();
        });
    }
}
