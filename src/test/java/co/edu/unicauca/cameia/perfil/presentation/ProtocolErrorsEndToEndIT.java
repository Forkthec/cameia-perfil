package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Peticiones mal formadas de protocolo contra el servidor real (puerto aleatorio y PostgreSQL real): ninguna
 * debe terminar en el 500 genérico ni en la forma por defecto de Spring. Se escriben en un socket crudo
 * para que {@code %zz} y los bytes de control lleguen tal cual.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProtocolErrorsEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String UID = "uid-cm335-a";
    private static final String PROFILE = "/api/v1/profiles/11111111-1111-1111-1111-111111111111";
    private static final String NOT_FOUND_ID = "22222222-2222-2222-2222-222222222222";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Test
    @DisplayName("Un PATCH con formulario mal codificado responde 415 con la forma común")
    void patch_shouldReturn415_whenFormIsMalformed() throws Exception {
        var reply = form("PATCH", PROFILE, "a=%zz");

        assertCommonProblem(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un PUT a una ruta inexistente con formulario mal codificado responde 404 ROUTE_NOT_FOUND")
    void put_shouldReturn404_whenRouteDoesNotExist() throws Exception {
        var reply = form("PUT", "/ruta-inexistente", "a=%zz");

        assertCommonProblem(reply, 404, "ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("Un DELETE a una ruta inexistente con formulario mal codificado responde 404 ROUTE_NOT_FOUND")
    void delete_shouldReturn404_whenRouteDoesNotExist() throws Exception {
        var reply = form("DELETE", "/ruta-inexistente", "a=%zz");

        assertCommonProblem(reply, 404, "ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("Un DELETE de una ruta real con formulario mal codificado da la respuesta normal de la ruta")
    void delete_shouldReturnNormalAnswer_whenFormIsMalformed() throws Exception {
        var reply = form("DELETE", PROFILE + "/skills/" + NOT_FOUND_ID, "a=%zz");

        assertCommonProblem(reply, 404, "PROFILE_NOT_FOUND");
    }

    @Test
    @DisplayName("Un PATCH con formulario bien formado sigue respondiendo 415")
    void patch_shouldStillReturn415_whenFormIsWellFormed() throws Exception {
        var reply = form("PATCH", PROFILE, "a=1");

        assertCommonProblem(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un PATCH con JSON ilegible sigue respondiendo 422 REQUEST_BODY_INVALID_FORMAT")
    void patch_shouldStillReturn422_whenJsonIsMalformed() throws Exception {
        var reply = new RawHttpClient(port).send("PATCH", PROFILE,
                List.of("Content-Type: application/json", "X-User-Id: " + UID), "{\"a\":");

        assertCommonProblem(reply, 422, "REQUEST_BODY_INVALID_FORMAT");
    }

    /** Envía un formulario con la identidad de prueba. */
    private RawHttpClient.Reply form(String method, String target, String body) throws IOException {
        var headers = new ArrayList<>(List.of("Content-Type: " + FORM, "X-User-Id: " + UID));
        return new RawHttpClient(port).send(method, target, headers, body);
    }

    /** Comprueba estado, código, identificador de la petición y tipo de contenido de la forma común. */
    private void assertCommonProblem(RawHttpClient.Reply reply, int status, String code) {
        assertThat(reply.status()).isEqualTo(status);
        assertThat(reply.headers().get("content-type")).isEqualTo("application/problem+json;charset=UTF-8");
        JsonNode json = mapper.readTree(reply.body());
        assertThat(json.get("code").asString()).isEqualTo(code);
        assertThat(json.get("requestId").asString()).isNotBlank();
        assertThat(reply.headers().get("x-request-id")).isEqualTo(json.get("requestId").asString());
    }
}
