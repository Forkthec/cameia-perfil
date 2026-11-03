package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.http.HttpResponse;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lista de perfiles del Usuario de punta a punta: servidor real en puerto aleatorio y PostgreSQL real. Cada prueba
 * usa sus propios Usuarios. Los perfiles adicionales se insertan con SQL porque el plan Free solo deja crear uno
 * por la API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ProfileListEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String DATA_ANALYST_ROLE = "a0000002-0000-0000-0000-000000000002";
    private static final String INSERT_PROFILE = """
            INSERT INTO perfil_profesional (id, firebase_uid, nombre, estado, creado_en, actualizado_en)
            VALUES (gen_random_uuid(), ?, ?, ?, ?::timestamptz, ?::timestamptz)
            """;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private ApiTestClient api;

    @BeforeEach
    void createClient() {
        api = new ApiTestClient(port);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-list-%'");
        jdbc.update("UPDATE rol_profesional SET estado = 'ACTIVE'");
    }

    private void insertProfile(String uid, String name, String status, String createdAt) {
        jdbc.update(INSERT_PROFILE, uid, name, status, createdAt, createdAt);
    }

    private HttpResponse<String> list(String uid, String query) throws Exception {
        return api.send("GET", "/api/v1/profiles" + query, uid, null);
    }

    @Test
    @DisplayName("Un Usuario sin perfiles recibe 200 con la lista vacía")
    void list_shouldReturnEmptyList_whenUserHasNoProfiles() throws Exception {
        var response = list("uid-list-empty", "");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(api.json(response).get("profiles")).isEmpty();
        assertThat(api.json(response).properties()).hasSize(1);
    }

    @Test
    @DisplayName("Con solo un borrador la lista trae ese borrador, distinguible de la lista vacía")
    void list_shouldReturnOnlyDrafts_whenNoneActive() throws Exception {
        var profileId = api.createProfile("uid-list-draft");

        var profiles = api.json(list("uid-list-draft", "")).get("profiles");

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).get("id").asString()).isEqualTo(profileId);
        assertThat(profiles.get(0).get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(profiles.get(0).get("name").isNull()).isTrue();
        assertThat(profiles.get(0).get("targetRoles")).isEmpty();
    }

    @Test
    @DisplayName("Con un solo perfil activo la lista trae ese perfil COMPLETED")
    void list_shouldReturnSingleActive_whenOnlyOne() throws Exception {
        insertProfile("uid-list-active", "Analista de datos", "COMPLETED", "2026-10-01T10:00:00Z");

        var profiles = api.json(list("uid-list-active", "")).get("profiles");

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(profiles.get(0).get("name").asString()).isEqualTo("Analista de datos");
    }

    @Test
    @DisplayName("Con un borrador y un activo la lista trae los dos, en orden de creación")
    void list_shouldReturnDraftsAndActive_whenUserHasBoth() throws Exception {
        insertProfile("uid-list-both", "Segundo", "IN_PROGRESS", "2026-10-02T10:00:00Z");
        insertProfile("uid-list-both", "Primero", "COMPLETED", "2026-10-01T10:00:00Z");

        var profiles = api.json(list("uid-list-both", "")).get("profiles");

        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(0).get("name").asString()).isEqualTo("Primero");
        assertThat(profiles.get(1).get("name").asString()).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("?status=COMPLETED devuelve solo los perfiles activos")
    void list_shouldFilterCompleted() throws Exception {
        insertProfile("uid-list-filter", "Borrador", "IN_PROGRESS", "2026-10-02T10:00:00Z");
        insertProfile("uid-list-filter", "Activo", "COMPLETED", "2026-10-01T10:00:00Z");

        var profiles = api.json(list("uid-list-filter", "?status=COMPLETED")).get("profiles");

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).get("name").asString()).isEqualTo("Activo");
    }

    @Test
    @DisplayName("Un identificador ajeno en la consulta se ignora: se listan solo los perfiles de quien llama")
    void list_shouldIgnoreForeignUidParameter() throws Exception {
        insertProfile("uid-list-owner-a", "De A", "COMPLETED", "2026-10-01T10:00:00Z");
        insertProfile("uid-list-owner-b", "De B", "COMPLETED", "2026-10-01T10:00:00Z");

        var asB = list("uid-list-owner-b", "?firebaseUid=uid-list-owner-a&uid=uid-list-owner-a&ownerId=uid-list-owner-a");

        assertThat(asB.statusCode()).isEqualTo(200);
        var profiles = api.json(asB).get("profiles");
        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).get("name").asString()).isEqualTo("De B");
    }

    @Test
    @DisplayName("Un rol agregado por la API sale con el id del catálogo y el nombre guardado")
    void list_shouldReturnTargetRolesWithCatalogIdAndTitle() throws Exception {
        var profileId = api.createProfile("uid-list-role");
        var added = api.send("POST", "/api/v1/profiles/" + profileId + "/target-roles", "uid-list-role",
                "{\"professionalRoleId\":\"" + DATA_ANALYST_ROLE + "\"}");
        assertThat(added.statusCode()).isEqualTo(201);

        var role = api.json(list("uid-list-role", "")).get("profiles").get(0).get("targetRoles").get(0);

        assertThat(role.properties()).hasSize(2);
        assertThat(role.get("professionalRoleId").asString()).isEqualTo(DATA_ANALYST_ROLE);
        assertThat(role.get("roleTitle").asString()).isNotBlank();
    }

    @Test
    @DisplayName("Un rol cuyo catálogo se desactivó después sigue en la lista")
    void list_shouldKeepRole_whenCatalogRoleInactive() throws Exception {
        var profileId = api.createProfile("uid-list-inactive");
        api.send("POST", "/api/v1/profiles/" + profileId + "/target-roles", "uid-list-inactive",
                "{\"professionalRoleId\":\"" + DATA_ANALYST_ROLE + "\"}");
        jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?::uuid", DATA_ANALYST_ROLE);

        var roles = api.json(list("uid-list-inactive", "")).get("profiles").get(0).get("targetRoles");

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).get("professionalRoleId").asString()).isEqualTo(DATA_ANALYST_ROLE);
    }

    @Test
    @DisplayName("Un estado repetido responde 422 PROFILE_STATUS_INVALID_VALUE")
    void list_shouldReturn422_whenStatusRepeated() throws Exception {
        var response = list("uid-list-repeat", "?status=COMPLETED&status=IN_PROGRESS");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("code").asString()).isEqualTo("PROFILE_STATUS_INVALID_VALUE");
    }

    @Test
    @DisplayName("Un estado hostil responde 422 y el cuerpo no lo repite")
    void list_shouldNotEchoStatus_whenHostile() throws Exception {
        var response = list("uid-list-hostile", "?status=COMPLETED%27--");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).doesNotContain("'--").doesNotContain("COMPLETED%27");
        assertThat(api.json(response).has("errors")).isFalse();
        assertThat(api.json(response).get("detail").asString()).isEqualTo("Selecciona una opción.");
    }

    @Test
    @DisplayName("Un estado vacío o de 1000 caracteres responde 422")
    void list_shouldReturn422_whenStatusEmptyOrHuge() throws Exception {
        assertThat(list("uid-list-empty-status", "?status=").statusCode()).isEqualTo(422);
        assertThat(list("uid-list-huge-status", "?status=" + "A".repeat(1000)).statusCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("Sin identidad responde 401 aunque el estado también sea inválido")
    void list_shouldReturn401_whenIdentityMissingAndStatusInvalid() throws Exception {
        var response = list(null, "?status=x");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(api.json(response).get("code").asString()).isEqualTo("IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres se acepta y una de 129 responde 401 IDENTITY_REQUIRED")
    void list_shouldAcceptIdentityOf128Characters_andRejectOf129() throws Exception {
        var accepted = list("a".repeat(128), "");
        var rejected = list("a".repeat(129), "");

        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(rejected.statusCode()).isEqualTo(401);
        assertThat(api.json(rejected).get("code").asString()).isEqualTo("IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("La respuesta declara charset UTF-8 y Cache-Control no-store")
    void list_shouldDeclareCharsetAndNoStore() throws Exception {
        var response = list("uid-list-headers", "");

        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).contains("application/json").containsIgnoringCase("charset=UTF-8"));
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    @DisplayName("El rechazo registra el código y no el valor del estado")
    void list_shouldNotLogStatusValue(CapturedOutput output) throws Exception {
        list("uid-list-log", "?status=VALOR-SECRETO");

        assertThat(output.getAll()).contains("PROFILE_STATUS_INVALID_VALUE").doesNotContain("VALOR-SECRETO");
    }

    @Test
    @DisplayName("Repetir la petición 20 veces da la misma lista y no cambia ninguna fila")
    void list_shouldReturnSameResult_whenRepeated20Times() throws Exception {
        var profileId = api.createProfile("uid-list-repeat20");
        api.send("POST", "/api/v1/profiles/" + profileId + "/target-roles", "uid-list-repeat20",
                "{\"professionalRoleId\":\"" + DATA_ANALYST_ROLE + "\"}");
        var profilesBefore = jdbc.queryForObject("SELECT count(*) FROM perfil_profesional", Long.class);
        var rolesBefore = jdbc.queryForObject("SELECT count(*) FROM rol_objetivo", Long.class);
        var bodies = new ArrayList<String>();

        for (int i = 0; i < 20; i++) {
            var response = list("uid-list-repeat20", "");
            assertThat(response.statusCode()).isEqualTo(200);
            bodies.add(response.body());
        }

        assertThat(bodies).hasSize(20).containsOnly(bodies.getFirst());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM perfil_profesional", Long.class)).isEqualTo(profilesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rol_objetivo", Long.class)).isEqualTo(rolesBefore);
    }
}
