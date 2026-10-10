package co.edu.unicauca.cameia.perfil.presentation;

import co.edu.unicauca.cameia.perfil.support.ReplicatedBirthDates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finalización del perfil de punta a punta: servidor real en puerto aleatorio y PostgreSQL real. Cada prueba crea su
 * propio Usuario para no depender de las demás.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CompletionEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String EDUCATION =
            "{\"institution\":\"Universidad del Cauca\",\"degree\":\"Ingenieria de Sistemas\",\"level\":\"UNDERGRADUATE\","
                    + "\"startDate\":\"2021-01\",\"endDate\":\"2025-12\",\"inProgress\":false}";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private ApiTestClient api;
    private final ArrayList<String> triggers = new ArrayList<>();

    @BeforeEach
    void createClient() {
        api = new ApiTestClient(port);
    }

    @AfterEach
    void cleanUp() {
        for (var suffix : triggers) {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_completion_" + suffix + " ON perfil_profesional");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_completion_" + suffix + "()");
        }
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-e2e-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbc, "uid-e2e-");
    }

    @Test
    @DisplayName("Un perfil que cumple los cinco requisitos se finaliza con 200 y queda COMPLETED")
    void completeProfile_shouldReturn200AndCompleted_whenAllRequirementsAreMet() throws Exception {
        var uid = newUid();
        var profileId = completeProfileData(uid, true);

        var response = complete(uid, profileId);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(api.json(response).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(status(uid, profileId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Sin experiencia laboral el perfil se finaliza igual")
    void completeProfile_shouldActivate_whenNoWorkExperience() throws Exception {
        var uid = newUid();
        var profileId = completeProfileData(uid, true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM experiencia_laboral WHERE perfil_id = ?::uuid", Integer.class, profileId))
                .isZero();

        assertThat(complete(uid, profileId).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sin resumen ni habilidades se listan solo esos dos requisitos, en orden, y el perfil sigue en Borrador")
    void completeProfile_shouldListSummaryAndSkills_whenBothAreMissing() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var base = "/api/v1/profiles/" + profileId;
        assertThat(api.send("PATCH", base, uid, "{\"name\":\"Perfil E2E\"}").statusCode()).isEqualTo(200);
        assertThat(api.send("POST", base + "/educations", uid, EDUCATION).statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/target-roles", uid, ROLE).statusCode()).isEqualTo(201);

        var response = complete(uid, profileId);

        assertThat(response.statusCode()).isEqualTo(422);
        var body = api.json(response);
        assertThat(body.get("code").asString()).isEqualTo("PROFILE_INCOMPLETE");
        assertThat(body.get("missingRequirements").valueStream().map(v -> v.asString()).toList())
                .containsExactly("SUMMARY", "SKILLS");
        assertThat(body.get("errors").valueStream().map(v -> v.get("field").asString()).toList())
                .containsExactly("summary", "skills");
        assertThat(status(uid, profileId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Un perfil nuevo lista los cinco requisitos en el orden de los criterios")
    void completeProfile_shouldListFiveInOrder_whenProfileIsNew() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var body = api.json(complete(uid, profileId));

        assertThat(body.get("missingRequirements").valueStream().map(v -> v.asString()).toList())
                .containsExactly("NAME", "SUMMARY", "EDUCATION", "SKILLS", "TARGET_ROLES");
        assertThat(body.get("errors").valueStream().map(v -> v.get("code").asString()).toList())
                .containsExactly("COMPLETION_NAME_REQUIRED", "COMPLETION_SUMMARY_REQUIRED", "COMPLETION_EDUCATION_REQUIRED",
                        "COMPLETION_SKILLS_REQUIRED", "COMPLETION_TARGET_ROLES_REQUIRED");
    }

    @Test
    @DisplayName("Un resumen guardado con el máximo de 2000 caracteres permite finalizar")
    void completeProfile_shouldActivate_whenStoredSummaryHasTheMaximumLength() throws Exception {
        var uid = newUid();
        var profileId = completeProfileData(uid, true);
        jdbc.update("UPDATE perfil_profesional SET resumen = repeat('a', 2000) WHERE id = ?::uuid", profileId);

        var response = complete(uid, profileId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(api.json(response).get("summary").asString()).hasSize(2000);
    }

    @Test
    @DisplayName("Si la base falla al guardar el estado, responde 500 genérico, no revela el fallo y el perfil no cambia")
    void completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion() throws Exception {
        var uid = newUid();
        var profileId = completeProfileData(uid, true);
        var suffix = UUID.randomUUID().toString().replace("-", "");
        triggers.add(suffix);
        jdbc.execute("CREATE FUNCTION fail_completion_" + suffix + "() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'forced failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_completion_" + suffix + " BEFORE UPDATE OF estado ON perfil_profesional "
                + "FOR EACH ROW WHEN (NEW.firebase_uid = '" + uid + "') EXECUTE FUNCTION fail_completion_" + suffix + "()");

        var response = complete(uid, profileId);

        assertThat(response.statusCode()).isEqualTo(500);
        var body = api.json(response);
        assertThat(body.get("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("detail").asString()).isEqualTo("Ocurrió un error. Inténtalo de nuevo.");
        assertThat(response.body()).doesNotContain("forced failure").doesNotContain("plpgsql");
        assertThat(status(uid, profileId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Finalizar el perfil de otro Usuario responde 403 y el perfil no cambia")
    void completeProfile_shouldReturn403_whenProfileBelongsToAnotherUser() throws Exception {
        var owner = newUid();
        var profileId = completeProfileData(owner, true);
        var stranger = newUid();

        var response = complete(stranger, profileId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(api.json(response).get("code").asString()).isEqualTo("PROFILE_NOT_ALLOWED");
        assertThat(status(owner, profileId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Veinte finalizaciones seguidas dejan un solo efecto: un 200 y diecinueve 409")
    void completeProfile_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes() throws Exception {
        var uid = newUid();
        var profileId = completeProfileData(uid, true);

        int completed = 0;
        int alreadyCompleted = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            var response = complete(uid, profileId);
            if (response.statusCode() == 200) {
                completed++;
            } else if (response.statusCode() == 409 && "PROFILE_ALREADY_COMPLETED".equals(api.json(response).get("code").asString())) {
                alreadyCompleted++;
            }
        }

        assertThat(completed).isEqualTo(1);
        assertThat(alreadyCompleted).isEqualTo(19);
        assertThat(status(uid, profileId)).isEqualTo("COMPLETED");
    }

    private static final String ROLE = "{\"professionalRoleId\":\"a0000001-0000-0000-0000-000000000002\"}";

    private HttpResponse<String> complete(String uid, String profileId) throws Exception {
        return api.send("POST", "/api/v1/profiles/" + profileId + "/completion", uid, null);
    }

    private String status(String uid, String profileId) throws Exception {
        return api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null)).get("status").asString();
    }

    /** Crea el perfil con nombre, resumen de 300 caracteres, una formación, una habilidad y un rol del catálogo. */
    private String completeProfileData(String uid, boolean withSummary) throws Exception {
        var profileId = api.createProfile(uid);
        var base = "/api/v1/profiles/" + profileId;
        var patch = withSummary ? "{\"name\":\"Perfil E2E\",\"summary\":\"" + "a".repeat(300) + "\"}" : "{\"name\":\"Perfil E2E\"}";
        assertThat(api.send("PATCH", base, uid, patch).statusCode()).isEqualTo(200);
        assertThat(api.send("POST", base + "/educations", uid, EDUCATION).statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/skills", uid, "{\"skillName\":\"Java\",\"level\":\"ADVANCED\"}").statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/target-roles", uid, ROLE).statusCode()).isEqualTo(201);
        return profileId;
    }

    /** Identidad nueva con su fecha de nacimiento replicada, como la dejaría el evento de cuenta creada. */
    private String newUid() {
        var uid = "uid-e2e-" + UUID.randomUUID();
        ReplicatedBirthDates.insertAdult(jdbc, uid);
        return uid;
    }
}
