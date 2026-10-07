package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba, con el servidor real y PostgreSQL real, que toda respuesta JSON declara
 * {@code charset=UTF-8} en {@code Content-Type}: la creación, la lectura y un error de ruta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ResponseCharsetIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final Pattern PROFILE_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    @DisplayName("Crear y leer un perfil responden JSON con charset UTF-8")
    void profileResponses_shouldDeclareUtf8_whenCreatedAndRead() throws Exception {
        var uid = "uid-charset-" + UUID.randomUUID();

        var created = send(HttpRequest.newBuilder(uri("/api/v1/profiles")).header("X-User-Id", uid)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(contentType(created)).contains("application/json").containsIgnoringCase("charset=UTF-8");

        var matcher = PROFILE_ID.matcher(created.body());
        assertThat(matcher.find()).isTrue();
        var read = send(HttpRequest.newBuilder(uri("/api/v1/profiles/" + matcher.group(1))).header("X-User-Id", uid).GET());
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(contentType(read)).containsIgnoringCase("charset=UTF-8");
    }

    @Test
    @DisplayName("Una ruta inexistente responde 404 con el código de ruta y charset UTF-8")
    void unknownRoute_shouldReturn404WithUtf8_whenRequested() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/v1/no-existe")).GET());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"ROUTE_NOT_FOUND\"");
        assertThat(contentType(response)).contains("application/problem+json").containsIgnoringCase("charset=UTF-8");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }
}
