package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Una prueba de punta a punta por criterio de aceptación de HU-2.11 (Roles Objetivo), con el servidor real en
 * puerto aleatorio y PostgreSQL real. Cada prueba usa su propio usuario para no depender de las demás.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TargetRolesEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String INACTIVE_ROLE = "a0000007-0000-0000-0000-000000000006";
    private static final String BACKEND_ROLE = "a0000001-0000-0000-0000-000000000002";
    private static final String DATA_SCIENTIST_ROLE = "a0000002-0000-0000-0000-000000000001";
    private static final String DATA_ANALYST_ROLE = "a0000002-0000-0000-0000-000000000002";

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-e2e-%'");
        jdbc.update("UPDATE rol_profesional SET estado = 'ACTIVE'");
    }

    @Test
    @DisplayName("CA-2.11.1: sustituir un rol conserva el Rol Objetivo, su id y su procedencia")
    void replace_shouldKeepTargetRole_whenCatalogRoleChanges() throws Exception {
        var profileId = createProfile("uid-e2e-1");
        var added = addRole("uid-e2e-1", profileId, DATA_ANALYST_ROLE);
        var roleId = json(added).get("targetRoles").get(0).get("id").asString();

        var replaced = send("PATCH", "/api/v1/profiles/" + profileId + "/target-roles/" + roleId, "uid-e2e-1",
                "{\"professionalRoleId\":\"" + DATA_SCIENTIST_ROLE + "\"}");

        assertThat(replaced.statusCode()).isEqualTo(200);
        var role = json(replaced).get("targetRoles").get(0);
        assertThat(role.get("id").asString()).isEqualTo(roleId);
        assertThat(role.get("roleTitle").asString()).isEqualTo("Científico de Datos");
        assertThat(role.get("provenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("CA-2.11.2: agregar un rol activo lo guarda con procedencia MANUAL aunque el cuerpo diga otra")
    void add_shouldStoreManualRole_whenRoleIsActive() throws Exception {
        var profileId = createProfile("uid-e2e-2");

        var response = send("POST", "/api/v1/profiles/" + profileId + "/target-roles", "uid-e2e-2",
                "{\"professionalRoleId\":\"" + BACKEND_ROLE + "\",\"provenance\":\"AI_SUGGESTED\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        var role = json(response).get("targetRoles").get(0);
        assertThat(role.get("provenance").asString()).isEqualTo("MANUAL");
        assertThat(role.get("roleTitle").asString()).isEqualTo("Desarrollador Backend");
    }

    @Test
    @DisplayName("CA-2.11.3: quitar el último rol de un perfil activo responde 409 y no cambia nada")
    void remove_shouldReturn409_whenLastRoleOfActiveProfile() throws Exception {
        var profileId = createProfile("uid-e2e-3");
        var roleId = json(addRole("uid-e2e-3", profileId, BACKEND_ROLE)).get("targetRoles").get(0).get("id").asString();
        completeProfile("uid-e2e-3", profileId);

        var response = send("DELETE", "/api/v1/profiles/" + profileId + "/target-roles/" + roleId, "uid-e2e-3", null);

        assertError(response, 409, "TARGET_ROLE_NOT_ALLOWED", "No puedes quedarte sin roles objetivo con el perfil activo.");
        var profile = json(get(profileId, "uid-e2e-3"));
        assertThat(profile.get("targetRoles")).hasSize(1);
        assertThat(profile.get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("CA-2.11.4: agregar el mismo rol dos veces responde 409 la segunda")
    void add_shouldReturn409_whenRoleRepeats() throws Exception {
        var profileId = createProfile("uid-e2e-4");
        assertThat(addRole("uid-e2e-4", profileId, BACKEND_ROLE).statusCode()).isEqualTo(201);

        var second = addRole("uid-e2e-4", profileId, BACKEND_ROLE);

        assertError(second, 409, "TARGET_ROLE_ALREADY_EXISTS", "Ese rol objetivo ya está en tu perfil.");
    }

    @Test
    @DisplayName("CA-2.11.5: el sexto rol responde 409 y el perfil conserva cinco")
    void add_shouldReturn409_whenSixthRole() throws Exception {
        var profileId = createProfile("uid-e2e-5");
        for (int i = 1; i <= 5; i++) {
            assertThat(addRole("uid-e2e-5", profileId, catalogRole(i)).statusCode()).isEqualTo(201);
        }

        var sixth = addRole("uid-e2e-5", profileId, catalogRole(6));

        assertError(sixth, 409, "TARGET_ROLE_LIMIT_REACHED", "Ya tienes el máximo de 5 roles objetivo.");
        assertThat(json(get(profileId, "uid-e2e-5")).get("targetRoles")).hasSize(5);
    }

    @Test
    @DisplayName("CA-2.11.7: un rol inactivo no se puede elegir y responde 422 en professionalRoleId")
    void add_shouldReturn422_whenRoleIsInactive() throws Exception {
        var profileId = createProfile("uid-e2e-7");
        jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?::uuid", INACTIVE_ROLE);

        var response = addRole("uid-e2e-7", profileId, INACTIVE_ROLE);

        assertThat(response.statusCode()).isEqualTo(422);
        var error = json(response).get("errors").get(0);
        assertThat(error.get("field").asString()).isEqualTo("professionalRoleId");
        assertThat(error.get("code").asString()).isEqualTo("PROFESSIONAL_ROLE_DISABLED");
        assertThat(json(get(profileId, "uid-e2e-7")).get("targetRoles")).isEmpty();
    }

    @Test
    @DisplayName("CA-2.11.8: sustituir por un rol que ya está en el perfil responde 409 y no cambia nada")
    void replace_shouldReturn409_whenRoleIsAlreadyInProfile() throws Exception {
        var profileId = createProfile("uid-e2e-8");
        var analystId = json(addRole("uid-e2e-8", profileId, DATA_ANALYST_ROLE)).get("targetRoles").get(0).get("id").asString();
        addRole("uid-e2e-8", profileId, DATA_SCIENTIST_ROLE);

        var response = send("PATCH", "/api/v1/profiles/" + profileId + "/target-roles/" + analystId, "uid-e2e-8",
                "{\"professionalRoleId\":\"" + DATA_SCIENTIST_ROLE + "\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).get("code").asString()).isEqualTo("TARGET_ROLE_ALREADY_EXISTS");
        var roles = json(get(profileId, "uid-e2e-8")).get("targetRoles");
        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).get("professionalRoleId").asString()).isEqualTo(DATA_ANALYST_ROLE);
        assertThat(roles.get(1).get("professionalRoleId").asString()).isEqualTo(DATA_SCIENTIST_ROLE);
    }

    @Test
    @DisplayName("CA-2.11.9: quitar el último rol de un perfil en Borrador responde 204")
    void remove_shouldReturn204_whenLastRoleOfDraft() throws Exception {
        var profileId = createProfile("uid-e2e-9");
        var roleId = json(addRole("uid-e2e-9", profileId, BACKEND_ROLE)).get("targetRoles").get(0).get("id").asString();

        var response = send("DELETE", "/api/v1/profiles/" + profileId + "/target-roles/" + roleId, "uid-e2e-9", null);

        assertThat(response.statusCode()).isEqualTo(204);
        var profile = json(get(profileId, "uid-e2e-9"));
        assertThat(profile.get("targetRoles")).isEmpty();
        assertThat(profile.get("status").asString()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("CA-2.11.10: el quinto rol se agrega y el perfil queda con cinco")
    void add_shouldReturn201_whenFifthRole() throws Exception {
        var profileId = createProfile("uid-e2e-10");
        for (int i = 1; i <= 4; i++) {
            addRole("uid-e2e-10", profileId, catalogRole(i));
        }

        var fifth = addRole("uid-e2e-10", profileId, catalogRole(5));

        assertThat(fifth.statusCode()).isEqualTo(201);
        assertThat(json(get(profileId, "uid-e2e-10")).get("targetRoles")).hasSize(5);
    }

    @Test
    @DisplayName("CA-2.11.11: el catálogo ofrece solo los roles activos")
    void catalog_shouldOfferOnlyActiveRoles_whenSomeAreInactive() throws Exception {
        jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?::uuid", INACTIVE_ROLE);
        var active = jdbc.queryForObject("SELECT count(*) FROM rol_profesional WHERE estado = 'ACTIVE'", Integer.class);

        var response = send("GET", "/api/v1/profiles/professional-roles", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        var ids = json(response).valueStream().map(r -> r.get("id").asString()).toList();
        assertThat(ids).doesNotContain(INACTIVE_ROLE).hasSize(active);
    }

    @Test
    @DisplayName("CA-2.11.12: el catálogo no contiene un texto que no es un rol")
    void catalog_shouldNotContainFreeText_whenSearchedTextIsNotARole() throws Exception {
        var response = send("GET", "/api/v1/profiles/professional-roles", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).valueStream().map(r -> r.get("nombre").asString())).doesNotContain("Astronauta");
    }

    @Test
    @DisplayName("CA-2.11.13: agregar, sustituir y quitar roles de un perfil ajeno responde 403")
    void writes_shouldReturn403_whenProfileBelongsToOtherUser() throws Exception {
        var profileId = createProfile("uid-e2e-a");
        var roleId = json(addRole("uid-e2e-a", profileId, BACKEND_ROLE)).get("targetRoles").get(0).get("id").asString();
        var base = "/api/v1/profiles/" + profileId + "/target-roles";

        var post = send("POST", base, "uid-e2e-b", "{\"professionalRoleId\":\"" + DATA_ANALYST_ROLE + "\"}");
        var patch = send("PATCH", base + "/" + roleId, "uid-e2e-b", "{\"professionalRoleId\":\"" + DATA_ANALYST_ROLE + "\"}");
        var delete = send("DELETE", base + "/" + roleId, "uid-e2e-b", null);

        for (var response : new HttpResponse<?>[] {post, patch, delete}) {
            assertError((HttpResponse<String>) response, 403, "PROFILE_NOT_ALLOWED", "No encontramos lo que buscabas.");
        }
        var roles = json(get(profileId, "uid-e2e-a")).get("targetRoles");
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).get("professionalRoleId").asString()).isEqualTo(BACKEND_ROLE);
    }

    /** Rol del catálogo número {@code n} de la categoría de desarrollo (a0000001-…-00000000000n). */
    private static String catalogRole(int n) {
        return "a0000001-0000-0000-0000-00000000000" + n;
    }

    private String createProfile(String uid) throws Exception {
        var response = send("POST", "/api/v1/profiles", uid, null);
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response).get("id").asString();
    }

    private HttpResponse<String> addRole(String uid, String profileId, String roleId) throws Exception {
        return send("POST", "/api/v1/profiles/" + profileId + "/target-roles", uid,
                "{\"professionalRoleId\":\"" + roleId + "\"}");
    }

    private HttpResponse<String> get(String profileId, String uid) throws Exception {
        return send("GET", "/api/v1/profiles/" + profileId, uid, null);
    }

    /** Agrega nombre, resumen, formación y habilidad, y finaliza el perfil (que ya tiene al menos un rol). */
    private void completeProfile(String uid, String profileId) throws Exception {
        var base = "/api/v1/profiles/" + profileId;
        assertThat(send("PATCH", base, uid, "{\"name\":\"Perfil E2E\",\"summary\":\"Resumen E2E de una persona desarrolladora.\","
                + "\"preferredModality\":\"HYBRID\",\"provenance\":\"MANUAL\"}").statusCode()).isEqualTo(200);
        assertThat(send("POST", base + "/educations", uid, "{\"institution\":\"Universidad del Cauca\",\"degree\":\"Ingenieria de Sistemas\","
                + "\"fieldOfStudy\":\"Ingenieria de Software\",\"level\":\"UNDERGRADUATE\",\"startDate\":\"2021-01\","
                + "\"endDate\":\"2025-12\",\"inProgress\":false,\"provenance\":\"MANUAL\"}").statusCode()).isEqualTo(201);
        assertThat(send("POST", base + "/skills", uid,
                "{\"skillName\":\"Java\",\"level\":\"ADVANCED\",\"provenance\":\"MANUAL\"}").statusCode()).isEqualTo(201);
        var completion = send("POST", base + "/completion", uid, null);
        assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
    }

    /** Comprueba estado, tipo de contenido, código y detalle de un error, y que no filtra detalles internos. */
    private void assertError(HttpResponse<String> response, int status, String code, String detail) {
        assertThat(response.statusCode()).isEqualTo(status);
        var contentType = response.headers().firstValue("Content-Type").orElse("");
        assertThat(contentType).contains("application/problem+json").containsIgnoringCase("charset=UTF-8");
        assertThat(json(response).get("code").asString()).isEqualTo(code);
        assertThat(json(response).get("detail").asString()).isEqualTo(detail);
        assertThat(response.body()).doesNotContain("Exception", "org.", "co.edu");
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String uid, String body)
            throws IOException, InterruptedException {
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
}
