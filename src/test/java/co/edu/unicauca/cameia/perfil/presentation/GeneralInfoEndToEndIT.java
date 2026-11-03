package co.edu.unicauca.cameia.perfil.presentation;

import co.edu.unicauca.cameia.perfil.support.ReplicatedBirthDates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Información general del perfil de punta a punta: servidor real en puerto aleatorio, PostgreSQL real y un reloj fijo.
 * Cada prueba crea su propio Usuario para no depender de las demás.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GeneralInfoEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Reloj fijo: las reglas de fechas de la formación que pide finalizar usan octubre de 2026. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final String EDUCATION_ENDED =
            "{\"institution\":\"Universidad del Cauca\",\"degree\":\"Ingenieria de Sistemas\",\"level\":\"UNDERGRADUATE\","
                    + "\"startDate\":\"2021-01\",\"endDate\":\"2025-12\",\"inProgress\":false}";

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
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-info-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbc, "uid-info-");
    }

    @Test
    @DisplayName("Un perfil en Borrador guarda el nombre y un resumen de 300 caracteres, sigue en curso y el resumen es MANUAL")
    void updateProfileInfo_shouldSaveNameAndSummary_whenDraft() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = patch(uid, profileId, "{\"name\":\"Analista de datos\",\"summary\":\"" + "a".repeat(300) + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        var profile = api.json(response);
        assertThat(profile.get("name").asString()).isEqualTo("Analista de datos");
        assertThat(profile.get("summary").asString()).hasSize(300);
        assertThat(profile.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(profile.get("summaryProvenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Un resumen vacío o con solo espacios lo borra, el perfil sigue en Borrador y al recargar sigue vacío")
    void updateProfileInfo_shouldClearSummary_whenDraftAndSummaryIsBlank() throws Exception {
        for (var blank : new String[] {"", "   "}) {
            var uid = newUid();
            var profileId = api.createProfile(uid);
            patch(uid, profileId, "{\"summary\":\"Resumen anterior\"}");

            var response = patch(uid, profileId, "{\"summary\":\"" + blank + "\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            assertSummaryCleared(api.json(response));
            var reloaded = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));
            assertSummaryCleared(reloaded);
            assertThat(reloaded.get("status").asString()).isEqualTo("IN_PROGRESS");
        }
    }

    @Test
    @DisplayName("Un nombre de 255 y un resumen de 2000 puntos de código con emojis se guardan completos y se releen iguales")
    void updateProfileInfo_shouldPersistLimits_whenNameHas255AndSummaryHas2000CodePoints() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var name = "😀".repeat(255);
        var summary = "😀".repeat(2000);

        var response = patch(uid, profileId, "{\"name\":\"" + name + "\",\"summary\":\"" + summary + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        var reloaded = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));
        assertThat(reloaded.get("name").asString()).isEqualTo(name);
        assertThat(reloaded.get("summary").asString()).isEqualTo(summary);
        assertThat(jdbc.queryForObject("SELECT char_length(nombre) FROM perfil_profesional WHERE id = ?::uuid",
                Integer.class, profileId)).isEqualTo(255);
        assertThat(jdbc.queryForObject("SELECT char_length(resumen) FROM perfil_profesional WHERE id = ?::uuid",
                Integer.class, profileId)).isEqualTo(2000);
    }

    @Test
    @DisplayName("Un perfil activo se renombra y sigue activo")
    void updateProfileInfo_shouldRenameAndKeepActive_whenProfileIsCompleted() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        completeProfile(uid, profileId);

        var response = patch(uid, profileId, "{\"name\":\"Analista de datos senior\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        var profile = api.json(response);
        assertThat(profile.get("name").asString()).isEqualTo("Analista de datos senior");
        assertThat(profile.get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Vaciar el resumen de un perfil activo responde 422 y conserva el resumen y el estado")
    void updateProfileInfo_shouldKeepSummary_whenActiveProfileClearsIt() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        completeProfile(uid, profileId);

        var response = patch(uid, profileId, "{\"name\":\"Otro nombre\",\"summary\":\"\"}");

        assertFieldError(response, "summary", "SUMMARY_NOT_ALLOWED",
                "No puedes quedarte sin resumen profesional con el perfil activo.");
        var profile = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));
        assertThat(profile.get("summary").asString()).isEqualTo("Resumen E2E de una persona desarrolladora.");
        assertThat(profile.get("name").asString()).isEqualTo("Perfil E2E");
        assertThat(profile.get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Editar el perfil de otro Usuario responde 403 y el perfil queda igual")
    void updateProfileInfo_shouldNotChangeOtherProfile_whenUserIsNotOwner() throws Exception {
        var owner = newUid();
        var intruder = newUid();
        var profileId = api.createProfile(owner);
        patch(owner, profileId, "{\"name\":\"Nombre del dueño\"}");

        var response = patch(intruder, profileId, "{\"name\":\"Intruso\",\"summary\":\"Resumen ajeno\"}");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(api.json(response).get("code").asString()).isEqualTo("PROFILE_NOT_ALLOWED");
        var profile = api.json(api.send("GET", "/api/v1/profiles/" + profileId, owner, null));
        assertThat(profile.get("name").asString()).isEqualTo("Nombre del dueño");
        assertThat(profile.get("summary").isNull()).isTrue();
    }

    @Test
    @DisplayName("El estado, la procedencia, la identidad y el id del cuerpo se ignoran")
    void updateProfileInfo_shouldIgnoreUnexpectedFields_whenBodyHasThem() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = patch(uid, profileId, "{\"name\":\"X\",\"status\":\"COMPLETED\",\"provenance\":\"AI_SUGGESTED\","
                + "\"firebaseUid\":\"otro\",\"id\":\"" + UUID.randomUUID() + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        var profile = api.json(response);
        assertThat(profile.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(profile.get("provenance").asString()).isEqualTo("MANUAL");
        assertThat(profile.get("id").asString()).isEqualTo(profileId);
        assertThat(jdbc.queryForObject("SELECT firebase_uid FROM perfil_profesional WHERE id = ?::uuid", String.class, profileId))
                .isEqualTo(uid);
    }

    @Test
    @DisplayName("Un resumen con marcado HTML se guarda y se devuelve como texto literal")
    void updateProfileInfo_shouldReturnTextLiterally_whenSummaryHasMarkup() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var markup = "<script>alert(1)</script>";

        var response = patch(uid, profileId, "{\"summary\":\"" + markup + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(api.json(response).get("summary").asString()).isEqualTo(markup);
        assertThat(jdbc.queryForObject("SELECT resumen FROM perfil_profesional WHERE id = ?::uuid", String.class, profileId))
                .isEqualTo(markup);
    }

    @Test
    @DisplayName("Un resumen de un mebibyte responde 422 SUMMARY_TOO_LONG, no 500, y no guarda nada")
    void updateProfileInfo_shouldReturn422_whenSummaryIsOneMebibyte() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = patch(uid, profileId, "{\"summary\":\"" + "a".repeat(1024 * 1024) + "\"}");

        assertFieldError(response, "summary", "SUMMARY_TOO_LONG", "El resumen no puede superar los 2000 caracteres.");
        assertThat(jdbc.queryForObject("SELECT resumen FROM perfil_profesional WHERE id = ?::uuid", String.class, profileId))
                .isNull();
    }

    @Test
    @DisplayName("Un carácter de control en el nombre responde 422 con su código, no 500, y no guarda nada")
    void updateProfileInfo_shouldRejectControlCharacter_whenNameHasNul() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = patch(uid, profileId, "{\"name\":\"Analista\\u0000\"}");

        assertFieldError(response, "name", "PROFILE_NAME_INVALID_CHARACTERS", "El nombre tiene caracteres no permitidos.");
        assertThat(jdbc.queryForObject("SELECT nombre FROM perfil_profesional WHERE id = ?::uuid", String.class, profileId))
                .isNull();
    }

    @Test
    @DisplayName("Repetir veinte veces el mismo PATCH deja el mismo perfil, salvo la fecha de actualización (FIA-02)")
    void updateProfileInfo_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var body = "{\"name\":\"Analista\",\"summary\":\"Resumen\"}";

        var first = withoutUpdatedAt(api.json(patch(uid, profileId, body)));
        for (int repetition = 2; repetition <= 20; repetition++) {
            var response = patch(uid, profileId, body);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(withoutUpdatedAt(api.json(response))).isEqualTo(first);
        }

        var reloaded = withoutUpdatedAt(api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null)));
        assertThat(reloaded).isEqualTo(first);
        assertThat(first.get("targetRoles")).isEmpty();
        assertThat(first.get("workExperiences")).isEmpty();
        assertThat(first.get("educations")).isEmpty();
        assertThat(first.get("profileSkills")).isEmpty();
    }

    private HttpResponse<String> patch(String uid, String profileId, String body) throws Exception {
        return api.send("PATCH", "/api/v1/profiles/" + profileId, uid, body);
    }

    private static void assertSummaryCleared(JsonNode profile) {
        assertThat(profile.get("summary").isNull()).isTrue();
        assertThat(profile.get("summaryProvenance").isNull()).isTrue();
    }

    private static JsonNode withoutUpdatedAt(JsonNode profile) {
        var copy = (ObjectNode) profile.deepCopy();
        copy.remove("updatedAt");
        return copy;
    }

    /** Agrega lo que pide finalizar (nombre, resumen, formación, habilidad y un rol objetivo) y finaliza el perfil. */
    private void completeProfile(String uid, String profileId) throws Exception {
        var base = "/api/v1/profiles/" + profileId;
        assertThat(patch(uid, profileId, "{\"name\":\"Perfil E2E\",\"summary\":\"Resumen E2E de una persona desarrolladora.\"}")
                .statusCode()).isEqualTo(200);
        assertThat(api.send("POST", base + "/educations", uid, EDUCATION_ENDED).statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/skills", uid,
                "{\"skillName\":\"Java\",\"level\":\"ADVANCED\",\"provenance\":\"MANUAL\"}").statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/target-roles", uid,
                "{\"professionalRoleId\":\"a0000001-0000-0000-0000-000000000002\"}").statusCode()).isEqualTo(201);
        var completion = api.send("POST", base + "/completion", uid, null);
        assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
    }

    /** Identidad nueva con su fecha de nacimiento replicada, como la dejaría el evento de cuenta creada. */
    private String newUid() {
        var uid = "uid-info-" + UUID.randomUUID();
        ReplicatedBirthDates.insertAdult(jdbc, uid);
        return uid;
    }

    /** Comprueba un 422 con un solo error de campo: su campo, código y mensaje literal. */
    private void assertFieldError(HttpResponse<String> response, String field, String code, String message) {
        assertThat(response.statusCode()).isEqualTo(422);
        var errors = api.json(response).get("errors");
        assertThat(errors.size()).isEqualTo(1);
        assertThat(errors.get(0).get("field").asString()).isEqualTo(field);
        assertThat(errors.get(0).get("code").asString()).isEqualTo(code);
        assertThat(errors.get(0).get("message").asString()).isEqualTo(message);
    }
}
