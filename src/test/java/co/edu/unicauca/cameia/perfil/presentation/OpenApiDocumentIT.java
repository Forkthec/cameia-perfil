package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el documento OpenAPI se genera con el servidor real y publica la forma común
 * de error con sus campos y los códigos de las respuestas de error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OpenApiDocumentIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Test
    @DisplayName("El OpenAPI publica el error común con code, requestId y errors, y nombra los códigos")
    void apiDocs_shouldDescribeCommonErrorAndCodes_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"ApiError\"", "\"ApiFieldError\"", "\"requestId\"", "\"missingRequirements\"")
                .contains("IDENTITY_REQUIRED", "PROFILE_ID_INVALID_FORMAT", "PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE");
    }
}
