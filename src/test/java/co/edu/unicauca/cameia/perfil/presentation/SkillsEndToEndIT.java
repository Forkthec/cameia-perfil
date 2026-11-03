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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Habilidades del perfil de punta a punta: servidor real en puerto aleatorio y PostgreSQL real. Cada prueba crea su
 * propio Usuario para no depender de las demás.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SkillsEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

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
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-skill-%'");
        ReplicatedBirthDates.deleteByPrefix(jdbc, "uid-skill-");
    }

    @Test
    @DisplayName("Una habilidad válida se guarda con procedencia MANUAL y el nombre recortado")
    void addSkill_shouldReturn201WithManualProvenance_whenSkillIsValid() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId, "{\"skillName\":\"  Python \",\"level\":\"INTERMEDIATE\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        var skill = api.json(response).get("profileSkills").get(0);
        assertThat(skill.get("skillName").asString()).isEqualTo("Python");
        assertThat(skill.get("level").asString()).isEqualTo("INTERMEDIATE");
        assertThat(skill.get("provenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Una habilidad que solo difiere en las tildes responde 409 bajo el campo skillName y no se guarda")
    void addSkill_shouldReturn409_whenNameDiffersOnlyInAccents() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        add(uid, profileId, "{\"skillName\":\"Programación\",\"level\":\"BASIC\"}");

        var repeated = add(uid, profileId, "{\"skillName\":\"programacion\",\"level\":\"ADVANCED\"}");

        assertThat(repeated.statusCode()).isEqualTo(409);
        var body = api.json(repeated);
        assertThat(body.get("code").asString()).isEqualTo("SKILL_ALREADY_EXISTS");
        assertThat(body.get("errors").get(0).get("field").asString()).isEqualTo("skillName");
        assertThat(count(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una habilidad con espacios internos repetidos y otra capitalización es la misma")
    void addSkill_shouldReturn409_whenNameDiffersOnlyInCaseAndInnerSpaces() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        add(uid, profileId, "{\"skillName\":\"Machine Learning\",\"level\":\"BASIC\"}");

        var repeated = add(uid, profileId, "{\"skillName\":\"  machine   LEARNING \",\"level\":\"BASIC\"}");

        assertThat(repeated.statusCode()).isEqualTo(409);
        assertThat(count(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("El campo provenance y cualquier otro campo no esperado se ignoran")
    void addSkill_shouldIgnoreUnexpectedFields_whenBodyHasThem() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId,
                "{\"skillName\":\"Go\",\"level\":\"BASIC\",\"provenance\":\"AI_SUGGESTED\",\"id\":\"x\",\"profileId\":\"y\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(api.json(response).get("profileSkills").get(0).get("provenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Un nombre de 1 MiB responde 422 SKILL_NAME_TOO_LONG y no 500")
    void addSkill_shouldReturn422_whenNameIsOneMebibyte() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId, "{\"skillName\":\"" + "a".repeat(1_048_576) + "\",\"level\":\"BASIC\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("errors").get(0).get("code").asString()).isEqualTo("SKILL_NAME_TOO_LONG");
    }

    @Test
    @DisplayName("Un carácter de control en el nombre responde 422 y no 500")
    void addSkill_shouldRejectControlCharacter_whenNameHasNul() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId, "{\"skillName\":\"Py\\u0000\",\"level\":\"BASIC\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("errors").get(0).get("code").asString()).isEqualTo("SKILL_NAME_INVALID_CHARACTERS");
        assertThat(count(profileId)).isZero();
    }

    @Test
    @DisplayName("La habilidad 21 responde 409 SKILL_LIMIT_REACHED y la repetida con 20 también")
    void addSkill_shouldReturn409LimitFirst_whenProfileHasTwenty() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        for (int index = 1; index <= 20; index++) {
            assertThat(add(uid, profileId, skillJson("skill-%02d".formatted(index))).statusCode()).isEqualTo(201);
        }

        var twentyFirst = add(uid, profileId, skillJson("skill-21"));
        var repeated = add(uid, profileId, skillJson("SKILL-01"));

        assertThat(twentyFirst.statusCode()).isEqualTo(409);
        assertThat(api.json(twentyFirst).get("code").asString()).isEqualTo("SKILL_LIMIT_REACHED");
        assertThat(api.json(repeated).get("code").asString()).isEqualTo("SKILL_LIMIT_REACHED");
        assertThat(count(profileId)).isEqualTo(20);
    }

    @Test
    @DisplayName("Quitar la única habilidad de un perfil en Borrador responde 204")
    void removeSkill_shouldReturn204_whenDraftHasOnlyOne() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var skillId = firstSkillId(add(uid, profileId, skillJson("Java")));

        var response = api.send("DELETE", skillPath(profileId, skillId), uid, null);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(count(profileId)).isZero();
    }

    @Test
    @DisplayName("Quitar la última habilidad de un perfil activo responde 409 SKILL_NOT_ALLOWED y la deja")
    void removeSkill_shouldReturn409AndKeepSkill_whenOnlySkillOfActiveProfile() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var skillId = firstSkillId(add(uid, profileId, skillJson("Java")));
        completeProfile(uid, profileId);

        var response = api.send("DELETE", skillPath(profileId, skillId), uid, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(api.json(response).get("code").asString()).isEqualTo("SKILL_NOT_ALLOWED");
        assertThat(count(profileId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM perfil_profesional WHERE id = ?::uuid", String.class, profileId))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Un perfil activo con dos habilidades puede quitar una")
    void removeSkill_shouldReturn204_whenActiveProfileHasTwo() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var skillId = firstSkillId(add(uid, profileId, skillJson("Java")));
        add(uid, profileId, skillJson("Python"));
        completeProfile(uid, profileId);

        var response = api.send("DELETE", skillPath(profileId, skillId), uid, null);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(count(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una habilidad de otro perfil responde 404 y la del otro perfil sigue")
    void removeSkill_shouldKeepOtherProfileSkill_whenIdBelongsToAnotherProfile() throws Exception {
        var owner = newUid();
        var ownerProfile = api.createProfile(owner);
        var ownerSkill = firstSkillId(add(owner, ownerProfile, skillJson("Java")));
        var other = newUid();
        var otherProfile = api.createProfile(other);
        add(other, otherProfile, skillJson("Python"));

        var response = api.send("DELETE", skillPath(otherProfile, ownerSkill), other, null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(api.json(response).get("code").asString()).isEqualTo("SKILL_NOT_FOUND");
        assertThat(count(ownerProfile)).isEqualTo(1);
        assertThat(count(otherProfile)).isEqualTo(1);
    }

    @Test
    @DisplayName("Las dos rutas responden 403 con el perfil de otro Usuario y no cambian nada")
    void skills_shouldReturn403_whenProfileBelongsToAnotherUser() throws Exception {
        var owner = newUid();
        var profileId = api.createProfile(owner);
        var skillId = firstSkillId(add(owner, profileId, skillJson("Java")));
        var stranger = newUid();

        var added = add(stranger, profileId, skillJson("Python"));
        var removed = api.send("DELETE", skillPath(profileId, skillId), stranger, null);

        assertThat(added.statusCode()).isEqualTo(403);
        assertThat(removed.statusCode()).isEqualTo(403);
        assertThat(count(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Repetir veinte veces la misma eliminación deja un solo efecto: un 204 y diecinueve 404")
    void removeSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var skillId = firstSkillId(add(uid, profileId, skillJson("Java")));
        add(uid, profileId, skillJson("Python"));

        int deleted = 0;
        int notFound = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            var response = api.send("DELETE", skillPath(profileId, skillId), uid, null);
            if (response.statusCode() == 204) {
                deleted++;
            } else if (response.statusCode() == 404 && "SKILL_NOT_FOUND".equals(api.json(response).get("code").asString())) {
                notFound++;
            }
        }

        assertThat(deleted).isEqualTo(1);
        assertThat(notFound).isEqualTo(19);
        assertThat(count(profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Repetir veinte veces el mismo alta deja un solo efecto: un 201 y diecinueve 409")
    void addSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        int created = 0;
        int duplicated = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            var response = add(uid, profileId, skillJson("Python"));
            if (response.statusCode() == 201) {
                created++;
            } else if (response.statusCode() == 409 && "SKILL_ALREADY_EXISTS".equals(api.json(response).get("code").asString())) {
                duplicated++;
            }
        }

        assertThat(created).isEqualTo(1);
        assertThat(duplicated).isEqualTo(19);
        assertThat(count(profileId)).isEqualTo(1);
    }

    private HttpResponse<String> add(String uid, String profileId, String body) throws Exception {
        return api.send("POST", "/api/v1/profiles/" + profileId + "/skills", uid, body);
    }

    private static String skillJson(String name) {
        return "{\"skillName\":\"" + name + "\",\"level\":\"BASIC\"}";
    }

    private static String skillPath(String profileId, String skillId) {
        return "/api/v1/profiles/" + profileId + "/skills/" + skillId;
    }

    private String firstSkillId(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return api.json(response).get("profileSkills").get(0).get("id").asString();
    }

    /** Agrega lo que pide finalizar (nombre, resumen, formación y un rol objetivo; la habilidad ya está) y finaliza el perfil. */
    private void completeProfile(String uid, String profileId) throws Exception {
        var base = "/api/v1/profiles/" + profileId;
        assertThat(api.send("PATCH", base, uid, "{\"name\":\"Perfil E2E\",\"summary\":\"Resumen E2E de una persona desarrolladora.\"}")
                .statusCode()).isEqualTo(200);
        assertThat(api.send("POST", base + "/educations", uid, EDUCATION_ENDED).statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/target-roles", uid,
                "{\"professionalRoleId\":\"a0000001-0000-0000-0000-000000000002\"}").statusCode()).isEqualTo(201);
        var completion = api.send("POST", base + "/completion", uid, null);
        assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
    }

    /** Identidad nueva con su fecha de nacimiento replicada, como la dejaría el evento de cuenta creada. */
    private String newUid() {
        var uid = "uid-skill-" + UUID.randomUUID();
        ReplicatedBirthDates.insertAdult(jdbc, uid);
        return uid;
    }

    private int count(String profileId) {
        return jdbc.queryForObject("SELECT count(*) FROM habilidad_perfil WHERE perfil_id = ?::uuid", Integer.class, profileId);
    }
}
