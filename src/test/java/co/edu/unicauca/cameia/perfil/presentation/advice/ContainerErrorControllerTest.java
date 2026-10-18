package co.edu.unicauca.cameia.perfil.presentation.advice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerErrorControllerTest {

    private final ContainerErrorController controller = new ContainerErrorController();
    private Logger controllerLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        controllerLogger = (Logger) LoggerFactory.getLogger(ContainerErrorController.class);
        logs = new ListAppender<>();
        logs.start();
        controllerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        controllerLogger.detachAppender(logs);
    }

    private static MockHttpServletRequest withStatus(int status) {
        var request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        return request;
    }

    @ParameterizedTest
    @CsvSource({"404,404,ROUTE_NOT_FOUND", "405,405,METHOD_NOT_ALLOWED", "406,406,MEDIA_TYPE_NOT_ACCEPTABLE",
            "415,415,MEDIA_TYPE_NOT_ALLOWED", "400,422,REQUEST_BODY_INVALID_FORMAT", "413,413,REQUEST_BODY_TOO_LARGE",
            "503,500,INTERNAL_ERROR", "501,500,INTERNAL_ERROR"})
    @DisplayName("Cada estado original del contenedor responde el código y el estado de su catálogo")
    void error_shouldMapStatus(int original, int expectedStatus, String expectedCode) {
        var response = controller.error(withStatus(original));

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        var problem = (ProblemDetail) response.getBody();
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.valueOf(expectedCode));
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/problem+json;charset=UTF-8");
    }

    @Test
    @DisplayName("Una petición directa a /error responde 404 ROUTE_NOT_FOUND y se registra en WARN")
    void error_shouldReturn404_whenNoOriginalStatus() {
        var response = controller.error(new MockHttpServletRequest("GET", "/error"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("code",
                ErrorCode.ROUTE_NOT_FOUND);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("ROUTE_NOT_FOUND");
        });
    }

    @Test
    @DisplayName("Conserva el identificador de la petición cuando el encabezado es válido")
    void error_shouldKeepRequestId_whenHeaderIsValid() {
        var request = withStatus(404);
        request.addHeader("X-Request-Id", "abc-1");

        var response = controller.error(request);

        assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("requestId", "abc-1");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("abc-1");
    }

    @Test
    @DisplayName("Genera un identificador nuevo cuando el encabezado no es válido")
    void error_shouldGenerateRequestId_whenHeaderIsInvalid() {
        var request = withStatus(404);
        request.addHeader("X-Request-Id", "a b");

        var response = controller.error(request);

        assertThat((String) ((ProblemDetail) response.getBody()).getProperties().get("requestId"))
                .isNotEqualTo("a b").matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("Usa la ruta original como instance")
    void error_shouldSetInstance_whenOriginalUriIsValid() {
        var request = withStatus(405);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/profiles/professional-roles");

        var response = controller.error(request);

        assertThat(((ProblemDetail) response.getBody()).getInstance().toString())
                .isEqualTo("/api/v1/profiles/professional-roles");
    }

    @Test
    @DisplayName("Omite instance sin fallar cuando la ruta original no es un URI válido")
    void error_shouldOmitInstance_whenOriginalUriIsMalformed() {
        var request = withStatus(404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/x/%zz");

        var response = controller.error(request);

        assertThat(((ProblemDetail) response.getBody()).getInstance()).isNull();
    }

    @Test
    @DisplayName("Un estado sin código propio se registra en ERROR con el estado, el origen de la excepción y sin la ruta")
    void error_shouldLogErrorWithOriginalStatusAndOrigin_whenGeneric() {
        var request = withStatus(503);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/ruta/secreta");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("dato personal"));

        controller.error(request);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("estado=503", "origen=" + ContainerErrorControllerTest.class.getName())
                    .doesNotContain("secreta").doesNotContain("dato personal");
        });
    }

    @Test
    @DisplayName("Sin excepción del contenedor, el ERROR de un estado sin código propio lleva origen - ")
    void error_shouldLogDashAsOrigin_whenContainerReportedNoException() {
        controller.error(withStatus(503));

        assertThat(logs.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("origen=- "));
    }

    @Test
    @DisplayName("Si el manejador ya registró el error, el despacho no escribe otra línea y conserva el requestId")
    void error_shouldNotLogAgainAndKeepRequestId_whenAlreadyLogged() {
        var request = withStatus(400);
        var web = new ServletWebRequest(request);
        var handled = ProblemResponses.requestId(web);
        ProblemResponses.markLogged(web);

        var response = controller.error(request);

        assertThat(logs.list).isEmpty();
        assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("requestId", handled);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(handled);
    }

    @Test
    @DisplayName("Sin encabezado X-Request-Id, la misma petición responde siempre con el mismo identificador")
    void error_shouldReuseGeneratedRequestId_whenRequestIsDispatchedTwice() {
        var request = withStatus(404);

        var first = controller.error(request).getHeaders().getFirst("X-Request-Id");
        var second = controller.error(request).getHeaders().getFirst("X-Request-Id");

        assertThat(first).isEqualTo(second);
    }
}
