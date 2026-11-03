package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Peticiones mal formadas de protocolo contra el servidor real (puerto aleatorio y PostgreSQL real): ninguna
 * debe terminar en el 500 genérico ni en la forma por defecto de Spring. Se escriben en un socket crudo
 * para que {@code %zz} y los bytes de control lleguen tal cual.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ProtocolErrorsEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String UID = "uid-cm335-a";
    private static final String PROFILE = "/api/v1/profiles/11111111-1111-1111-1111-111111111111";
    private static final String ROLES = "/api/v1/profiles/professional-roles";
    private static final String QUERY_CODE = "REQUEST_QUERY_INVALID_FORMAT";
    private static final String NOT_FOUND_ID = "22222222-2222-2222-2222-222222222222";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    ServletContext servletContext;

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

    @ParameterizedTest
    @ValueSource(strings = {"/work-experiences", "/educations", "/skills", "/target-roles"})
    @DisplayName("Un POST con cuerpo y formulario mal codificado responde 415 en cada ruta con cuerpo")
    void post_shouldReturn415_whenFormIsMalformed(String route) throws Exception {
        var reply = form("POST", PROFILE + route, "a=%zz");

        assertCommonProblem(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/skills", ""})
    @DisplayName("Un multipart sin boundary responde 415 REQUEST_MULTIPART_INVALID_FORMAT")
    void post_shouldReturn415_whenMultipartHasNoBoundary(String route) throws Exception {
        var target = route.isEmpty() ? "/api/v1/profiles" : PROFILE + route;
        var reply = new RawHttpClient(port).send("POST", target,
                List.of("Content-Type: multipart/form-data", "X-User-Id: " + UID), "x");

        assertCommonProblem(reply, 415, "REQUEST_MULTIPART_INVALID_FORMAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/skills", "/work-experiences"})
    @DisplayName("Un formulario mal codificado de 3 MB responde 415")
    void post_shouldReturn415_whenFormIsThreeMegabytes(String route) throws Exception {
        var reply = form("POST", PROFILE + route, "a=%zz&b=" + "x".repeat(3 * 1024 * 1024));

        assertCommonProblem(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un formulario mal codificado con 10 050 parámetros responde 415")
    void post_shouldReturn415_whenFormHasMoreParametersThanTheContainerReads() throws Exception {
        var body = IntStream.range(0, 10_050).mapToObj(i -> "p" + i + "=1").collect(Collectors.joining("&")) + "&a=%zz";

        assertCommonProblem(form("POST", PROFILE + "/skills", body), 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {";;;", "*/*;q=abc"})
    @DisplayName("Un Accept ilegible no quita el cuerpo común a un 422, un 415, un 404 ni un 405")
    void accept_shouldKeepCommonBody_whenAcceptIsUnreadable(String accept) throws Exception {
        var client = new RawHttpClient(port);
        var json = List.of("Content-Type: application/json", "X-User-Id: " + UID, "Accept: " + accept);
        var formHeaders = List.of("Content-Type: " + FORM, "X-User-Id: " + UID, "Accept: " + accept);

        assertCommonProblem(client.send("PATCH", PROFILE, json, "{\"a\":"), 422, "REQUEST_BODY_INVALID_FORMAT");
        assertCommonProblem(client.send("GET", ROLES + "?lang=%zz", List.of("Accept: " + accept), null), 422, QUERY_CODE);
        assertCommonProblem(client.send("PATCH", PROFILE, formHeaders, "a=1"), 415, "MEDIA_TYPE_NOT_ALLOWED");
        assertCommonProblem(client.send("GET", "/ruta-inexistente", List.of("Accept: " + accept), null), 404, "ROUTE_NOT_FOUND");
        assertCommonProblem(client.send("PUT", "/api/v1/profiles", List.of("X-User-Id: " + UID, "Accept: " + accept), null),
                405, "METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un formulario mal codificado se registra una vez en WARN, con el código, el requestId y la identidad")
    void post_shouldLogOnceAtWarn_whenFormIsMalformed(CapturedOutput output) throws Exception {
        var requestId = "cm335-form-" + System.nanoTime();

        var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills",
                List.of("Content-Type: " + FORM, "X-User-Id: " + UID, "X-Request-Id: " + requestId), "a=%zzSECRETO");

        assertCommonProblem(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
        assertThat(linesWith(output, requestId)).singleElement().satisfies(line -> assertThat(line)
                .contains(" WARN ", "code=MEDIA_TYPE_NOT_ALLOWED", "firebaseUid=" + UID).doesNotContain("SECRETO"));
    }

    @Test
    @DisplayName("Un multipart sin boundary se registra una vez en WARN, con el código, el requestId y la identidad")
    void post_shouldLogOnceAtWarn_whenMultipartHasNoBoundary(CapturedOutput output) throws Exception {
        var requestId = "cm335-multipart-" + System.nanoTime();

        var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills",
                List.of("Content-Type: multipart/form-data", "X-User-Id: " + UID, "X-Request-Id: " + requestId), "x");

        assertCommonProblem(reply, 415, "REQUEST_MULTIPART_INVALID_FORMAT");
        assertThat(linesWith(output, requestId)).singleElement().satisfies(line -> assertThat(line)
                .contains(" WARN ", "code=REQUEST_MULTIPART_INVALID_FORMAT", "firebaseUid=" + UID));
    }

    @Test
    @DisplayName("Un cuerpo troceado con un trozo mal formado responde 422 REQUEST_BODY_INVALID_FORMAT con la ruta original y un solo registro")
    void post_shouldAnswerOnceWithBodyInvalidFormat_whenChunkedBodyIsMalformed(CapturedOutput output) throws Exception {
        var requestId = "cm335-chunk-" + System.nanoTime();

        var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills",
                List.of("Content-Type: application/json", "Transfer-Encoding: chunked", "X-User-Id: " + UID,
                        "X-Request-Id: " + requestId), "zz\r\nabc\r\n0\r\n\r\n");

        assertCommonProblem(reply, 422, "REQUEST_BODY_INVALID_FORMAT");
        var json = mapper.readTree(reply.body());
        assertThat(json.get("detail").asString()).isEqualTo("Revisa el formato de los datos enviados.");
        assertThat(json.get("instance").asString()).isEqualTo(PROFILE + "/skills");
        assertThat(json.get("requestId").asString()).isEqualTo(requestId);
        assertThat(linesWith(output, requestId)).singleElement().satisfies(line -> assertThat(line)
                .contains(" WARN ", "code=REQUEST_BODY_INVALID_FORMAT", "firebaseUid=" + UID));
    }

    @Test
    @DisplayName("Sin X-Request-Id, un cuerpo troceado mal formado responde con el identificador que quedó en el único registro")
    void post_shouldKeepGeneratedRequestId_whenChunkedBodyIsMalformedWithoutRequestIdHeader(CapturedOutput output)
            throws Exception {
        var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills",
                List.of("Content-Type: application/json", "Transfer-Encoding: chunked", "X-User-Id: " + UID),
                "zz\r\nabc\r\n0\r\n\r\n");

        assertCommonProblem(reply, 422, "REQUEST_BODY_INVALID_FORMAT");
        var requestId = mapper.readTree(reply.body()).get("requestId").asString();
        assertThat(linesWith(output, requestId)).singleElement().satisfies(line -> assertThat(line)
                .contains(" WARN ", "code=REQUEST_BODY_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Un multipart de 0,5 MB bien formado conserva su respuesta de siempre: 415 de tipo no admitido")
    void post_shouldKeepMediaTypeAnswer_whenMultipartIsHalfMegabyte() throws Exception {
        var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills", multipartHeaders(null),
                multipart(1, 512 * 1024, true));

        assertCommonProblem(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Una parte multipart de más de 1 MiB (archivo o texto) responde 413 REQUEST_BODY_TOO_LARGE en WARN y sin archivos temporales")
    void post_shouldReturn413_whenMultipartPartIsTooLarge(boolean file, CapturedOutput output) throws Exception {
        for (var bytes : new int[] {1_572_864, 11 * 1024 * 1024}) {
            var before = filesInUploadTempDir();
            var requestId = "cm335-big-" + System.nanoTime();

            var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills", multipartHeaders(requestId),
                    multipart(1, bytes, file));

            assertTooLarge(reply);
            assertThat(linesWith(output, requestId)).singleElement().satisfies(line -> assertThat(line)
                    .contains(" WARN ", "code=REQUEST_BODY_TOO_LARGE", "firebaseUid=" + UID).doesNotContain("xxxx"));
            assertThat(filesInUploadTempDir()).as("archivos temporales con %s bytes", bytes).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("El tope por parte es exacto: 1 MiB llega a la ruta (415) y 1 MiB más un byte responde 413")
    void post_shouldAcceptExactlyOneMebibyte_andRejectOneByteMore() throws Exception {
        var client = new RawHttpClient(port);

        assertCommonProblem(client.send("POST", PROFILE + "/skills", multipartHeaders(null), multipart(1, 1_048_576, true)),
                415, "MEDIA_TYPE_NOT_ALLOWED");
        assertTooLarge(client.send("POST", PROFILE + "/skills", multipartHeaders(null), multipart(1, 1_048_577, true)));
    }

    @Test
    @DisplayName("Un multipart de más de 10 MiB en total, con partes de menos de 1 MiB, responde 413 sin archivos temporales")
    void post_shouldReturn413_whenMultipartRequestIsTooLarge() throws Exception {
        var before = filesInUploadTempDir();

        var reply = new RawHttpClient(port).send("POST", PROFILE + "/skills", multipartHeaders(null),
                multipart(11, 996_147, true));

        assertTooLarge(reply);
        assertThat(filesInUploadTempDir()).isEqualTo(before);
    }

    @Test
    @DisplayName("Un multipart grande en una consulta GET sin identidad también responde 413")
    void get_shouldReturn413_whenMultipartIsTooLarge() throws Exception {
        var headers = List.of("Content-Type: " + MULTIPART);

        assertTooLarge(new RawHttpClient(port).send("GET", ROLES, headers, multipart(1, 1_572_864, false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?lang=%zz", "?lang=%zz&lang=en", "?%zz=1"})
    @DisplayName("Una consulta del catálogo de roles mal codificada responde 422 sin repetir el valor")
    void get_shouldReturn422_whenQueryIsMalformed(String query) throws Exception {
        var reply = get(ROLES + query, null);

        assertQueryProblem(reply);
    }

    @Test
    @DisplayName("El filtro status mal codificado de la lista de perfiles responde 422")
    void list_shouldReturn422_whenStatusIsMalformed() throws Exception {
        var reply = get("/api/v1/profiles?status=%zz", UID);

        assertQueryProblem(reply);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?lang=%00", "?lang=xx", "?lang=%C3%B1"})
    @DisplayName("Un idioma bien codificado pero desconocido conserva su código propio")
    void get_shouldKeepLanguageCode_whenLangIsWellFormedButUnknown(String query) throws Exception {
        var reply = get(ROLES + query, null);

        assertCommonProblem(reply, 422, "PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE");
    }

    @Test
    @DisplayName("Un parámetro que nadie lee no se decodifica y no rompe la petición")
    void get_shouldNotFail_whenUnreadParameterIsMalformed() throws Exception {
        var reply = get(PROFILE + "?x=%zz", UID);

        assertCommonProblem(reply, 404, "PROFILE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"?lang=%C3", "?lang=%FF"})
    @DisplayName("Un escape cuyos bytes no son UTF-8 válido responde 422 REQUEST_QUERY_INVALID_FORMAT")
    void get_shouldReturn422_whenPercentEncodingBytesAreNotUtf8(String query) throws Exception {
        var reply = get(ROLES + query, null);

        assertQueryProblem(reply);
    }

    @Test
    @DisplayName("Un parámetro de 1000 caracteres con un escape malo al final responde 422 sin eco")
    void get_shouldReturn422_whenParameterIsVeryLong() throws Exception {
        var reply = get(ROLES + "?lang=" + "a".repeat(1000) + "%zz", null);

        assertQueryProblem(reply);
        assertThat(reply.body()).doesNotContain("aaaa");
    }

    @Test
    @DisplayName("Repetir 20 veces la misma consulta mala da 20 respuestas iguales")
    void get_shouldReturnSameAnswer_whenRepeated20Times() throws Exception {
        for (var i = 0; i < 20; i++) {
            assertQueryProblem(get(ROLES + "?lang=%zz", null));
        }
    }

    @Test
    @DisplayName("50 consultas malas en paralelo responden 422 sin ningún 5xx")
    void get_shouldNotFail_whenManyMalformedRequestsRunInParallel() throws Exception {
        try (var pool = Executors.newFixedThreadPool(50)) {
            var calls = IntStream.range(0, 50).mapToObj(i -> pool.submit(() -> get(ROLES + "?lang=%zz", null))).toList();
            for (var call : calls) {
                assertQueryProblem(call.get());
            }
        }
    }

    @Test
    @DisplayName("El registro del rechazo lleva el código y nunca el valor recibido")
    void get_shouldNotLogMalformedValue(CapturedOutput output) throws Exception {
        get(ROLES + "?lang=%zzSECRETO", null);

        assertThat(output.getAll()).contains(QUERY_CODE).doesNotContain("SECRETO");
    }

    @Test
    @DisplayName("Un TRACE responde 405 con la forma común y conserva el Allow del contenedor")
    void trace_shouldReturn405WithCommonShape() throws Exception {
        var reply = new RawHttpClient(port).send("TRACE", ROLES, List.of(), null);

        assertCommonProblem(reply, 405, "METHOD_NOT_ALLOWED");
        assertThat(reply.headers().get("allow")).as("el Allow del contenedor sobrevive al TRACE").isNotBlank();
    }

    @Test
    @DisplayName("Una petición directa a /error responde 404 ROUTE_NOT_FOUND con la forma común")
    void error_shouldReturnCommonShape_whenRouteIsRequestedDirectly() throws Exception {
        var reply = get("/error", null);

        assertCommonProblem(reply, 404, "ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("Una identidad con una tabulación responde 401 IDENTITY_REQUIRED")
    void get_shouldReturn401_whenIdentityHasTab() throws Exception {
        var reply = new RawHttpClient(port).send("GET", PROFILE, List.of("X-User-Id: uid\tx"), null);

        assertCommonProblem(reply, 401, "IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("Una identidad con los controles C1 0x85 y 0x9f responde 401 IDENTITY_REQUIRED")
    void get_shouldReturn401_whenIdentityHasC1Control() throws Exception {
        var client = new RawHttpClient(port);

        assertCommonProblem(client.send("GET", PROFILE, List.of("X-User-Id: uid\u0085x"), null), 401, "IDENTITY_REQUIRED");
        assertCommonProblem(client.send("GET", PROFILE, List.of("X-User-Id: uid\u009fx"), null), 401, "IDENTITY_REQUIRED");
    }

    private static final String MULTIPART = "multipart/form-data; boundary=cm335";

    /** Encabezados de un multipart con la identidad de prueba y, si se da, un identificador de petición. */
    private static List<String> multipartHeaders(String requestId) {
        var headers = new ArrayList<>(List.of("Content-Type: " + MULTIPART, "X-User-Id: " + UID));
        if (requestId != null) {
            headers.add("X-Request-Id: " + requestId);
        }
        return headers;
    }

    /** Cuerpo multipart con {@code parts} partes de {@code bytesPerPart} bytes, de archivo o de texto. */
    private static String multipart(int parts, int bytesPerPart, boolean file) {
        var body = new StringBuilder();
        for (var i = 0; i < parts; i++) {
            body.append("--cm335\r\nContent-Disposition: form-data; name=\"f").append(i).append('"');
            if (file) {
                body.append("; filename=\"f").append(i).append(".bin\"\r\nContent-Type: application/octet-stream");
            }
            body.append("\r\n\r\n").append("x".repeat(bytesPerPart)).append("\r\n");
        }
        return body.append("--cm335--\r\n").toString();
    }

    /** Comprueba el 413 con su mensaje fijo, sin citar los límites ni lo recibido. */
    private void assertTooLarge(RawHttpClient.Reply reply) {
        assertCommonProblem(reply, 413, "REQUEST_BODY_TOO_LARGE");
        assertThat(mapper.readTree(reply.body()).get("detail").asString()).isEqualTo("Cuerpo de la petición demasiado grande.");
        assertThat(reply.body()).doesNotContain("1048576").doesNotContain("10485760").doesNotContain("xxxx");
    }

    /** Archivos regulares que el contenedor dejó en la carpeta temporal de las cargas. */
    private long filesInUploadTempDir() throws IOException {
        var dir = ((File) servletContext.getAttribute(ServletContext.TEMPDIR)).toPath();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    /** Líneas del registro que mencionan el identificador de la petición. */
    private static List<String> linesWith(CapturedOutput output, String requestId) {
        return output.getAll().lines().filter(line -> line.contains("requestId=" + requestId)).toList();
    }

    /** Petición GET; con {@code uid} nulo no envía identidad. */
    private RawHttpClient.Reply get(String target, String uid) throws IOException {
        var headers = uid == null ? List.<String>of() : List.of("X-User-Id: " + uid);
        return new RawHttpClient(port).send("GET", target, headers, null);
    }

    /** Comprueba el 422 de consulta mal codificada: mensaje fijo, sin lista de campos y sin eco del valor. */
    private void assertQueryProblem(RawHttpClient.Reply reply) {
        assertCommonProblem(reply, 422, QUERY_CODE);
        JsonNode json = mapper.readTree(reply.body());
        assertThat(json.get("detail").asString()).isEqualTo("Revisa los datos enviados.");
        assertThat(json.has("errors")).isFalse();
        assertThat(reply.body()).doesNotContain("zz").doesNotContain("SECRETO");
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
