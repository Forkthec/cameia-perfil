package co.edu.unicauca.cameia.perfil.presentation;

import com.jayway.jsonpath.JsonPath;
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
    @DisplayName("El OpenAPI publica el error común y sus códigos, y no publica los endpoints sin HU del MVP")
    void apiDocs_shouldDescribeCommonErrorAndCodes_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"ApiError\"", "\"ApiFieldError\"", "\"requestId\"", "\"missingRequirements\"")
                .contains("IDENTITY_REQUIRED", "PROFILE_ID_INVALID_FORMAT", "PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE")
                .contains("WORK_EXPERIENCE_NOT_FOUND", "TARGET_ROLE_NOT_FOUND", "END_DATE_BEFORE_START_DATE")
                // Sin HU en el MVP (hallazgo I-024): no se publica para que Frontend no lo integre.
                .doesNotContain("/review-requests", "/salary-expectation");
    }

    @Test
    @DisplayName("El PATCH del rol objetivo describe su campo igual que el POST: uuid, ejemplo y obligatorio")
    void apiDocs_shouldDescribeUpdateTargetRoleBody_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        var schema = "$.components.schemas.UpdateTargetRoleRequest";
        assertThat(JsonPath.<String>read(body, schema + ".properties.professionalRoleId.format")).isEqualTo("uuid");
        assertThat(JsonPath.<String>read(body, schema + ".properties.professionalRoleId.description")).isNotBlank();
        assertThat(JsonPath.<String>read(body, schema + ".properties.professionalRoleId.example"))
                .isEqualTo("a0000001-0000-0000-0000-000000000003");
        assertThat(JsonPath.<java.util.List<String>>read(body, schema + ".required")).containsExactly("professionalRoleId");
    }
}
