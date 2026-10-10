package co.edu.unicauca.cameia.perfil.presentation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Cliente HTTP mínimo para las pruebas de punta a punta: habla con el servidor real en su puerto aleatorio,
 * manda la identidad en {@code X-User-Id} como lo haría el Gateway y lee las respuestas como JSON.
 */
final class ApiTestClient {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final int port;

    ApiTestClient(int port) {
        this.port = port;
    }

    /**
     * Envía una petición.
     *
     * @param method método HTTP
     * @param path   ruta que empieza en {@code /api}
     * @param uid    identidad del Usuario, o {@code null} para no enviar el encabezado
     * @param body   cuerpo JSON, o {@code null} si no lleva
     * @return la respuesta con el cuerpo como texto
     * @throws IOException          si falla la conexión
     * @throws InterruptedException si se interrumpe la espera
     */
    HttpResponse<String> send(String method, String path, String uid, String body) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (uid != null) {
            builder.header("X-User-Id", uid);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * @param response respuesta con cuerpo JSON
     * @return el cuerpo como árbol JSON
     */
    JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    /**
     * Crea el perfil del Usuario.
     *
     * @param uid identidad del Usuario
     * @return el identificador del perfil creado
     * @throws IOException          si falla la conexión
     * @throws InterruptedException si se interrumpe la espera
     */
    String createProfile(String uid) throws IOException, InterruptedException {
        return json(send("POST", "/api/v1/profiles", uid, null)).get("id").asString();
    }
}
