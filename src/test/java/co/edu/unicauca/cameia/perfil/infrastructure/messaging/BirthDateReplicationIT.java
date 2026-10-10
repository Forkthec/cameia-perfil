package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import co.edu.unicauca.cameia.perfil.application.service.BirthDateReplicaAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Réplica de la fecha de nacimiento de punta a punta: un mensaje publicado en RabbitMQ real llega al consumidor, se aplica en
 * PostgreSQL real y, si incumple el contrato, termina en la cola de fallidos de su origen.
 *
 * <p>Los mensajes se construyen a mano, con el cuerpo literal del contrato y las propiedades de un productor, nunca con un
 * convertidor de la prueba: así se comprueba el contrato y no el código de la propia prueba.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class BirthDateReplicationIT extends MessagingIntegrationSupport {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String ALREADY_PROCESSED = "Mensaje ya procesado; se ignora";

    @MockitoSpyBean
    BirthDateReplicaAppService service;

    @BeforeEach
    void clean() {
        resetState();
        Mockito.reset(service);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private String storedBirthDate(String userId) {
        return jdbcTemplate.queryForObject(
                "select cast(fecha_nacimiento as varchar) from fecha_nacimiento_usuario where firebase_uid = ?",
                String.class, userId);
    }

    @Test
    @DisplayName("Un evento válido guarda la fecha de nacimiento, anota el mensaje en el Inbox y no deja fallidos")
    void accountCreated_shouldStoreBirthDate_whenEventIsValid() {
        var messageId = newId();

        publish(CREATED_KEY, CREATED_BODY, messageId);

        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));
        assertThat(storedBirthDate(USER_ID)).isEqualTo(BIRTH_DATE);
        assertThat(jdbcTemplate.queryForObject(
                "select tipo from evento_procesado where message_id = ?::uuid", String.class, messageId))
                .isEqualTo("cuenta.creada");
        assertThat(messageCount(CREATED_DLQ)).isZero();
    }

    @Test
    @DisplayName("El mismo mensaje 20 veces deja una fila y una anotación, y 19 se ignoran")
    void accountCreated_shouldKeepOneRowAndOneInboxEntry_whenSameMessageArrives20Times(CapturedOutput output) {
        var messageId = newId();

        for (int i = 0; i < 20; i++) {
            publish(CREATED_KEY, CREATED_BODY, messageId);
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(messageCount(CREATED_QUEUE)).isZero();
            assertThat(output.getAll().split(ALREADY_PROCESSED, -1).length - 1).isEqualTo(19);
        });
        assertThat(replicaRows()).isEqualTo(1);
        assertThat(inboxRows()).isEqualTo(1);
        assertThat(messageCount(CREATED_DLQ)).isZero();
    }

    @Test
    @DisplayName("Una reentrega del mismo mensaje tras el commit no tiene efecto porque el Inbox la reconoce")
    void accountCreated_shouldHaveNoEffect_whenRedeliveredAfterCommit() {
        var messageId = newId();
        publish(CREATED_KEY, CREATED_BODY, messageId);
        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));
        // Se borra la fila para que un segundo efecto sea visible.
        jdbcTemplate.update("delete from fecha_nacimiento_usuario");

        publish(CREATED_KEY, CREATED_BODY, messageId);

        await().atMost(WAIT).untilAsserted(() -> assertThat(messageCount(CREATED_QUEUE)).isZero());
        Mockito.verify(service, Mockito.timeout(10_000).times(2)).recordAccountCreated(Mockito.any());
        assertThat(replicaRows()).isZero();
        assertThat(inboxRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un mensaje sin identificador va a la cola de fallidos sin tocar la réplica ni el Inbox")
    void accountCreated_shouldGoToDeadLetterQueue_whenMessageIdIsMissing() {
        publish(CREATED_KEY, CREATED_BODY, null);

        awaitDeadLettered(CREATED_QUEUE, CREATED_DLQ);
        assertThat(replicaRows()).isZero();
        assertThat(inboxRows()).isZero();
    }

    @Test
    @DisplayName("Un identificador que no es UUID va a la cola de fallidos sin tocar la réplica ni el Inbox")
    void accountCreated_shouldGoToDeadLetterQueue_whenMessageIdIsNotUuid() {
        publish(CREATED_KEY, CREATED_BODY, "abc");

        awaitDeadLettered(CREATED_QUEUE, CREATED_DLQ);
        assertThat(replicaRows()).isZero();
        assertThat(inboxRows()).isZero();
    }

    @Test
    @DisplayName("Un campo que el servicio no lee se ignora y la fecha se guarda igual")
    void accountCreated_shouldIgnoreExtraFields_whenPresent() {
        var body = CREATED_BODY.replace("}", ",\"rol\":\"ADMIN\"}");

        publish(CREATED_KEY, body, newId());

        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));
        assertThat(storedBirthDate(USER_ID)).isEqualTo(BIRTH_DATE);
    }

    @Test
    @DisplayName("El encabezado __TypeId__ no elige la clase que se instancia")
    void accountCreated_shouldIgnoreTypeIdHeader_whenPresent() {
        publish(CREATED_KEY, CREATED_BODY.getBytes(StandardCharsets.UTF_8), newId(), "java.util.HashMap");

        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));
        assertThat(storedBirthDate(USER_ID)).isEqualTo(BIRTH_DATE);
    }

    @Test
    @DisplayName("Quien nació el 29 de febrero de un año bisiesto se guarda con esa fecha")
    void accountCreated_shouldStoreLeapDay_whenBornOnFebruary29() {
        publish(CREATED_KEY, CREATED_BODY.replace(BIRTH_DATE, "2008-02-29"), newId());

        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));
        assertThat(storedBirthDate(USER_ID)).isEqualTo("2008-02-29");
    }

    static Stream<Arguments> invalidBodies() {
        var tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        return Stream.of(
                Arguments.of("no es json", "no es json"),
                Arguments.of("arreglo", "[]"),
                Arguments.of("cuerpo vacío", ""),
                Arguments.of("sin usuarioId", "{\"fechaNacimiento\":\"2008-03-15\"}"),
                Arguments.of("usuarioId nulo", "{\"usuarioId\":null,\"fechaNacimiento\":\"2008-03-15\"}"),
                Arguments.of("usuarioId en blanco", "{\"usuarioId\":\"   \",\"fechaNacimiento\":\"2008-03-15\"}"),
                Arguments.of("usuarioId de 129 caracteres",
                        "{\"usuarioId\":\"" + "a".repeat(129) + "\",\"fechaNacimiento\":\"2008-03-15\"}"),
                Arguments.of("usuarioId de 1 048 576 caracteres",
                        "{\"usuarioId\":\"" + "a".repeat(1_048_576) + "\",\"fechaNacimiento\":\"2008-03-15\"}"),
                Arguments.of("usuarioId objeto", "{\"usuarioId\":{\"a\":1},\"fechaNacimiento\":\"2008-03-15\"}"),
                Arguments.of("sin fechaNacimiento", "{\"usuarioId\":\"" + USER_ID + "\"}"),
                Arguments.of("fecha dd/mm/aaaa", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":\"15/03/2008\"}"),
                Arguments.of("fecha con mes de un dígito", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":\"2008-3-15\"}"),
                Arguments.of("fecha con hora", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":\"2008-03-15T00:00:00Z\"}"),
                Arguments.of("30 de febrero", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":\"2008-02-30\"}"),
                Arguments.of("29 de febrero de un año no bisiesto", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":\"2007-02-29\"}"),
                Arguments.of("fecha numérica", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":20080315}"),
                Arguments.of("fecha de mañana", "{\"usuarioId\":\"" + USER_ID + "\",\"fechaNacimiento\":\"" + tomorrow + "\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("Un cuerpo que incumple el contrato va a la cola de fallidos y no cambia la réplica")
    void accountCreated_shouldGoToDeadLetterQueue_whenPayloadIsInvalid(String name, String body) {
        publish(CREATED_KEY, body, newId());

        awaitDeadLettered(CREATED_QUEUE, CREATED_DLQ);
        assertThat(replicaRows()).isZero();
        assertThat(inboxRows()).isZero();
        // Sin reintentos: el consumidor se invoca una vez como máximo (cero si el cuerpo ni se convierte).
        assertThat(Mockito.mockingDetails(service).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("recordAccountCreated")).count()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Un usuarioId numérico se convierte a texto y se guarda")
    void accountCreated_shouldAcceptNumericUserId_whenSent() {
        publish(CREATED_KEY, "{\"usuarioId\":12345,\"fechaNacimiento\":\"2008-03-15\"}", newId());

        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));
        assertThat(storedBirthDate("12345")).isEqualTo(BIRTH_DATE);
    }

    private static final String DELETED_BODY =
            "{\"usuarioId\":\"" + USER_ID + "\",\"eliminadaEn\":\"2026-10-20T03:00:00.000Z\"}";

    private void insertReplica() {
        jdbcTemplate.update("insert into fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento) values (?, ?::date)",
                USER_ID, BIRTH_DATE);
    }

    @Test
    @DisplayName("Una cuenta eliminada borra la fecha de nacimiento")
    void accountDeleted_shouldRemoveBirthDate_whenPresent() {
        insertReplica();

        publish(DELETED_KEY, DELETED_BODY, newId());

        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isZero());
    }

    @Test
    @DisplayName("Una cuenta eliminada sin fila se confirma, se anota en el Inbox y no va a fallidos")
    void accountDeleted_shouldAck_whenAbsent() {
        var messageId = newId();

        publish(DELETED_KEY, DELETED_BODY, messageId);

        await().atMost(WAIT).untilAsserted(() -> assertThat(inboxRows()).isEqualTo(1));
        assertThat(jdbcTemplate.queryForObject(
                "select tipo from evento_procesado where message_id = ?::uuid", String.class, messageId))
                .isEqualTo("cuenta.eliminada");
        assertThat(messageCount(DELETED_QUEUE)).isZero();
        assertThat(messageCount(DELETED_DLQ)).isZero();
    }

    @Test
    @DisplayName("Un cuenta.eliminada ya procesado no tiene efecto al repetirse")
    void accountDeleted_shouldHaveNoEffect_whenMessageWasProcessed() {
        var messageId = newId();
        publish(DELETED_KEY, DELETED_BODY, messageId);
        await().atMost(WAIT).untilAsserted(() -> assertThat(inboxRows()).isEqualTo(1));
        insertReplica();

        publish(DELETED_KEY, DELETED_BODY, messageId);

        await().atMost(WAIT).untilAsserted(() -> assertThat(messageCount(DELETED_QUEUE)).isZero());
        Mockito.verify(service, Mockito.timeout(10_000).times(2)).recordAccountDeleted(Mockito.any());
        assertThat(replicaRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cuenta eliminada con identidad en blanco va a la cola de fallidos de eliminada")
    void accountDeleted_shouldGoToDeadLetterQueue_whenUserIdIsBlank() {
        publish(DELETED_KEY, "{\"usuarioId\":\"   \"}", newId());

        awaitDeadLettered(DELETED_QUEUE, DELETED_DLQ);
        assertThat(inboxRows()).isZero();
    }

    @Test
    @DisplayName("Procesar un evento no escribe la fecha de nacimiento, el correo ni el cuerpo en el registro")
    void accountCreated_shouldNotLogBirthDateNorEmail_whenProcessed(CapturedOutput output) {
        publish(CREATED_KEY, CREATED_BODY, newId());
        await().atMost(WAIT).untilAsserted(() -> assertThat(replicaRows()).isEqualTo(1));

        assertThat(output.getAll())
                .contains(USER_ID)
                .doesNotContain(BIRTH_DATE)
                .doesNotContain(EMAIL)
                .doesNotContain(CREATED_BODY);
    }
}
