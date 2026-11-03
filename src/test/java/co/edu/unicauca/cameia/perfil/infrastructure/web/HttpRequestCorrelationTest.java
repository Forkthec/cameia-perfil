package co.edu.unicauca.cameia.perfil.infrastructure.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/** El identificador de la petición se lee del encabezado de la petición en curso, y solo de ahí. */
class HttpRequestCorrelationTest {

    private final HttpRequestCorrelation correlation = new HttpRequestCorrelation();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("Devuelve el X-Request-Id tal como llegó")
    void currentRequestId_shouldReturnHeader_whenRequestHasIt() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-123");
        bind(request);

        assertThat(correlation.currentRequestId()).contains("req-123");
    }

    @Test
    @DisplayName("Sin el encabezado no hay identificador")
    void currentRequestId_shouldBeEmpty_whenHeaderIsMissing() {
        bind(new MockHttpServletRequest());

        assertThat(correlation.currentRequestId()).isEmpty();
    }

    @Test
    @DisplayName("Fuera de una petición, como en el Job de relevo, no hay identificador")
    void currentRequestId_shouldBeEmpty_whenNoRequestIsBound() {
        assertThat(correlation.currentRequestId()).isEmpty();
    }
}
