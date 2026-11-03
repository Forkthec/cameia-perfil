package co.edu.unicauca.cameia.perfil.infrastructure.messaging;

import co.edu.unicauca.cameia.perfil.application.service.BirthDateReplicaAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reintentos de los consumidores de eventos de cuenta contra RabbitMQ y PostgreSQL reales: un mensaje que incumple el
 * contrato no se reintenta y un fallo técnico se intenta exactamente tres veces antes de ir a la cola de fallidos.
 */
@ExtendWith(OutputCaptureExtension.class)
class AccountEventsRetryIT extends MessagingIntegrationSupport {

    @MockitoSpyBean
    BirthDateReplicaAppService service;

    @BeforeEach
    void clean() {
        resetState();
        Mockito.reset(service);
    }

    private static long count(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1L;
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON no llega al servicio, va a fallidos y se registra una sola vez")
    void retry_shouldNotInvokeService_whenBodyIsNotJson(CapturedOutput output) {
        publish(CREATED_KEY, "no es json", UUID.randomUUID().toString());

        awaitDeadLettered(CREATED_QUEUE, CREATED_DLQ);
        Mockito.verify(service, Mockito.never()).recordAccountCreated(Mockito.any());
        assertThat(count(output.getAll(), "razón=PAYLOAD_INVALID_FORMAT")).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("Mensaje de cuenta agotado").doesNotContain("Execution of Rabbit message listener failed");
    }

    @Test
    @DisplayName("Un mensaje que incumple el contrato en el servicio se invoca una vez y va a fallidos con un solo registro")
    void retry_shouldInvokeOnce_whenMessageBreaksContract(CapturedOutput output) {
        publish(CREATED_KEY, "{\"usuarioId\":\"   \",\"fechaNacimiento\":\"2008-03-15\"}", UUID.randomUUID().toString());

        awaitDeadLettered(CREATED_QUEUE, CREATED_DLQ);
        Mockito.verify(service, Mockito.times(1)).recordAccountCreated(Mockito.any());
        assertThat(count(output.getAll(), "razón=USER_ID_INVALID")).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("Mensaje de cuenta agotado").doesNotContain("Execution of Rabbit message listener failed");
    }

    @Test
    @DisplayName("Un fallo de la base de datos se intenta tres veces, se registra en ERROR una vez y va a fallidos")
    void retry_shouldAttemptThreeTimes_whenDatabaseFails(CapturedOutput output) {
        Mockito.doThrow(new DataAccessResourceFailureException("base de datos caída"))
                .when(service).recordAccountCreated(Mockito.any());

        publish(CREATED_KEY, CREATED_BODY, UUID.randomUUID().toString());

        awaitDeadLettered(CREATED_QUEUE, CREATED_DLQ);
        Mockito.verify(service, Mockito.times(3)).recordAccountCreated(Mockito.any());
        assertThat(count(output.getAll(), "Mensaje de cuenta agotado tras los reintentos")).isEqualTo(1);
        // El contenedor de Spring no repite el fallo con su propio WARN y la traza no lleva el texto de la excepción.
        assertThat(output.getAll()).contains("causa=DataAccessResourceFailureException")
                .doesNotContain("Execution of Rabbit message listener failed").doesNotContain("base de datos caída")
                .doesNotContain(CREATED_BODY).doesNotContain(BIRTH_DATE).doesNotContain(EMAIL);
        assertThat(replicaRows()).isZero();
    }
}
