package co.edu.unicauca.cameia.perfil.presentation;

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

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiencia laboral de punta a punta: servidor real en puerto aleatorio, PostgreSQL real y un reloj fijo en
 * octubre de 2026. Cada prueba crea su propio Usuario para no depender de las demás.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WorkHistoryEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Reloj fijo: el mes actual para las reglas de fechas es octubre de 2026. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final String CURRENT = "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\",\"startDate\":\"2024-06\"";

    private static final String EDUCATION_BASE =
            "{\"institution\":\"Universidad del Cauca\",\"degree\":\"Ingenieria de Sistemas\",\"level\":\"UNDERGRADUATE\",\"startDate\":\"2025-02\"";
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
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-work-%'");
    }

    @Test
    @DisplayName("Una experiencia en curso se guarda sin fecha de fin y con procedencia MANUAL")
    void addWorkExperience_shouldReturn201WithNullEndDate_whenCurrent() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId, CURRENT + "}");

        assertThat(response.statusCode()).isEqualTo(201);
        var experience = api.json(response).get("workExperiences").get(0);
        assertThat(experience.get("endDate").isNull()).isTrue();
        assertThat(experience.get("employmentStatus").asString()).isEqualTo("CURRENT");
        assertThat(experience.get("provenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Una experiencia con fin desconocido se guarda sin fechas ni descripción")
    void addWorkExperience_shouldReturn201WithoutDates_whenUnknownEndHasNoDates() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var withoutDates = add(uid, profileId, "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"UNKNOWN_END\"}");
        var withStart = add(uid, profileId,
                "{\"company\":\"Globex\",\"position\":\"Dev\",\"employmentStatus\":\"UNKNOWN_END\",\"startDate\":\"2024-06\"}");

        assertThat(withoutDates.statusCode()).isEqualTo(201);
        var first = api.json(withoutDates).get("workExperiences").get(0);
        assertThat(first.get("startDate").isNull()).isTrue();
        assertThat(first.get("endDate").isNull()).isTrue();
        assertThat(first.get("description").isNull()).isTrue();
        var second = api.json(withStart).get("workExperiences").get(1);
        assertThat(second.get("startDate").asString()).isEqualTo("2024-06");
        assertThat(second.get("endDate").isNull()).isTrue();
    }

    @Test
    @DisplayName("Al leer el perfil, una experiencia sin inicio muestra la fecha de inicio nula")
    void getProfile_shouldReturnNullStartDate_whenExperienceHasNoStart() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        add(uid, profileId, "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"UNKNOWN_END\"}");

        var profile = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));

        assertThat(profile.get("workExperiences").get(0).get("startDate").isNull()).isTrue();
    }

    @Test
    @DisplayName("Dos periodos que se superponen se aceptan")
    void addWorkExperience_shouldAcceptOverlap_whenPeriodsOverlap() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var ended = add(uid, profileId,
                "{\"company\":\"A\",\"position\":\"Dev\",\"employmentStatus\":\"ENDED\",\"startDate\":\"2023-03\",\"endDate\":\"2024-12\"}");
        var current = add(uid, profileId, CURRENT + "}");

        assertThat(ended.statusCode()).isEqualTo(201);
        assertThat(current.statusCode()).isEqualTo(201);
        assertThat(api.json(current).get("workExperiences")).hasSize(2);
    }

    @Test
    @DisplayName("Dos registros idénticos se aceptan como dos experiencias")
    void addWorkExperience_shouldAcceptIdenticalRecords_whenSentTwice() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        add(uid, profileId, CURRENT + "}");
        var second = add(uid, profileId, CURRENT + "}");

        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(api.json(second).get("workExperiences")).hasSize(2);
    }

    @Test
    @DisplayName("Los campos que no son del contrato se ignoran: el perfil no cambia de estado ni de dueño")
    void addWorkExperience_shouldIgnoreUnexpectedFields_whenBodyHasThem() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId, CURRENT + ",\"id\":\"" + UUID.randomUUID() + "\",\"profileId\":\"" + UUID.randomUUID()
                + "\",\"status\":\"COMPLETED\",\"firebaseUid\":\"otro\",\"seniority\":\"JUNIOR\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(api.json(response).get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT firebase_uid FROM perfil_profesional WHERE id = ?::uuid", String.class, profileId))
                .isEqualTo(uid);
    }

    @Test
    @DisplayName("Un texto con marcado se guarda y se devuelve tal cual, nunca como HTML")
    void addWorkExperience_shouldReturnTextLiterally_whenTextHasMarkup() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var markup = "<script>alert(1)</script>";

        var response = add(uid, profileId, CURRENT + ",\"description\":\"" + markup + "\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(api.json(response).get("workExperiences").get(0).get("description").asString()).isEqualTo(markup);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM experiencia_laboral WHERE perfil_id = ?::uuid", String.class,
                profileId)).isEqualTo(markup);
    }

    @Test
    @DisplayName("Un carácter de control en la empresa responde 422 y no 500")
    void addWorkExperience_shouldRejectControlCharacter_whenCompanyHasNul() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId,
                "{\"company\":\"Acme\\u0000\",\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("errors").get(0).get("code").asString()).isEqualTo("COMPANY_INVALID_CHARACTERS");
    }

    @Test
    @DisplayName("Una descripción de 1 MiB responde 422 DESCRIPTION_TOO_LONG")
    void addWorkExperience_shouldReturn422_whenDescriptionIsOneMebibyte() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = add(uid, profileId, CURRENT + ",\"description\":\"" + "a".repeat(1_048_576) + "\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("errors").get(0).get("code").asString()).isEqualTo("DESCRIPTION_TOO_LONG");
    }

    @Test
    @DisplayName("Quitar la única experiencia de un perfil activo responde 204 y el perfil sigue activo")
    void removeWorkExperience_shouldReturn204AndKeepActive_whenOnlyExperienceOfActiveProfile() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var experienceId = api.json(add(uid, profileId, CURRENT + "}")).get("workExperiences").get(0).get("id").asString();
        completeProfile(uid, profileId);

        var response = api.send("DELETE", "/api/v1/profiles/" + profileId + "/work-experiences/" + experienceId, uid, null);

        assertThat(response.statusCode()).isEqualTo(204);
        var profile = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));
        assertThat(profile.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(profile.get("workExperiences")).isEmpty();
    }

    @Test
    @DisplayName("Una experiencia de otro perfil no se puede quitar por la ruta de este y sigue en su perfil")
    void removeWorkExperience_shouldKeepOtherProfileExperience_whenIdBelongsToAnotherProfile() throws Exception {
        var uidA = newUid();
        var uidB = newUid();
        var profileA = api.createProfile(uidA);
        var profileB = api.createProfile(uidB);
        var experienceOfB = api.json(add(uidB, profileB, CURRENT + "}")).get("workExperiences").get(0).get("id").asString();

        var response = api.send("DELETE", "/api/v1/profiles/" + profileA + "/work-experiences/" + experienceOfB, uidA, null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(api.json(response).get("code").asString()).isEqualTo("WORK_EXPERIENCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM experiencia_laboral WHERE perfil_id = ?::uuid", Integer.class,
                profileB)).isEqualTo(1);
    }

    @Test
    @DisplayName("Quien no es el dueño recibe 403 al agregar y al quitar, y el perfil no cambia")
    void workHistory_shouldNotChangeOtherProfile_whenUserIsNotOwner() throws Exception {
        var owner = newUid();
        var intruder = newUid();
        var profileId = api.createProfile(owner);
        var experienceId = api.json(add(owner, profileId, CURRENT + "}")).get("workExperiences").get(0).get("id").asString();

        var post = add(intruder, profileId, CURRENT + "}");
        var delete = api.send("DELETE", "/api/v1/profiles/" + profileId + "/work-experiences/" + experienceId, intruder, null);

        assertThat(post.statusCode()).isEqualTo(403);
        assertThat(delete.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM experiencia_laboral WHERE perfil_id = ?::uuid", Integer.class,
                profileId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Repetir veinte veces la eliminación no tiene más efecto que la primera")
    void removeWorkExperience_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var first = api.json(add(uid, profileId, CURRENT + "}")).get("workExperiences").get(0).get("id").asString();
        add(uid, profileId, "{\"company\":\"Globex\",\"position\":\"Dev\",\"employmentStatus\":\"UNKNOWN_END\"}");
        var path = "/api/v1/profiles/" + profileId + "/work-experiences/" + first;

        assertThat(api.send("DELETE", path, uid, null).statusCode()).isEqualTo(204);
        for (int i = 0; i < 19; i++) {
            var repeated = api.send("DELETE", path, uid, null);
            assertThat(repeated.statusCode()).isEqualTo(404);
            assertThat(api.json(repeated).get("code").asString()).isEqualTo("WORK_EXPERIENCE_NOT_FOUND");
        }

        var profile = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));
        assertThat(profile.get("workExperiences")).hasSize(1);
        assertThat(profile.get("workExperiences").get(0).get("company").asString()).isEqualTo("Globex");
        assertThat(profile.get("status").asString()).isEqualTo("IN_PROGRESS");
    }

    // ── Formación académica ───────────────────────────────────────────────

    @Test
    @DisplayName("Una formación en curso se guarda sin fecha de fin y con procedencia MANUAL")
    void addEducation_shouldReturn201WithNullEndDate_whenInProgress() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = addEducation(uid, profileId, EDUCATION_BASE + ",\"inProgress\":true}");

        assertThat(response.statusCode()).isEqualTo(201);
        var education = api.json(response).get("educations").get(0);
        assertThat(education.get("startDate").asString()).isEqualTo("2025-02");
        assertThat(education.get("endDate").isNull()).isTrue();
        assertThat(education.get("inProgress").asBoolean()).isTrue();
        assertThat(education.get("provenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Una formación terminada en el mes actual se acepta y el mes siguiente se rechaza")
    void addEducation_shouldValidateAgainstUtcMonth_whenEndIsCurrentOrNext() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var current = addEducation(uid, profileId, EDUCATION_BASE + ",\"endDate\":\"2026-10\"}");
        var next = addEducation(uid, profileId, EDUCATION_BASE + ",\"endDate\":\"2026-11\"}");

        assertThat(current.statusCode()).isEqualTo(201);
        assertThat(next.statusCode()).isEqualTo(422);
        assertThat(api.json(next).get("errors").get(0).get("code").asString()).isEqualTo("END_DATE_IN_THE_FUTURE");
    }

    @Test
    @DisplayName("Un perfil en Borrador puede eliminar su única formación")
    void removeEducation_shouldReturn204_whenDraftHasOnlyOne() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        var educationId = api.json(addEducation(uid, profileId, EDUCATION_ENDED)).get("educations").get(0).get("id").asString();

        var response = api.send("DELETE", "/api/v1/profiles/" + profileId + "/educations/" + educationId, uid, null);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null)).get("educations")).isEmpty();
    }

    @Test
    @DisplayName("Eliminar la última formación de un perfil activo responde 409 y la formación sigue")
    void removeEducation_shouldReturn409_whenLastEducationOfActiveProfile() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        completeProfile(uid, profileId);
        var educationId = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null))
                .get("educations").get(0).get("id").asString();

        var response = api.send("DELETE", "/api/v1/profiles/" + profileId + "/educations/" + educationId, uid, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(api.json(response).get("code").asString()).isEqualTo("EDUCATION_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM educacion WHERE perfil_id = ?::uuid", Integer.class, profileId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un perfil activo con dos formaciones elimina una y sigue activo")
    void removeEducation_shouldReturn204_whenActiveProfileHasTwo() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        completeProfile(uid, profileId);
        var second = api.json(addEducation(uid, profileId, EDUCATION_BASE + ",\"inProgress\":true}"))
                .get("educations").get(1).get("id").asString();

        var response = api.send("DELETE", "/api/v1/profiles/" + profileId + "/educations/" + second, uid, null);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null)).get("status").asString())
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("La sexta formación responde 409 y la base conserva cinco")
    void addEducation_shouldReturn409_whenProfileAlreadyHasFive() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        for (int i = 0; i < 5; i++) {
            assertThat(addEducation(uid, profileId, EDUCATION_BASE + "}").statusCode()).isEqualTo(201);
        }

        var sixth = addEducation(uid, profileId, EDUCATION_BASE + "}");

        assertThat(sixth.statusCode()).isEqualTo(409);
        assertThat(api.json(sixth).get("code").asString()).isEqualTo("EDUCATION_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM educacion WHERE perfil_id = ?::uuid", Integer.class, profileId))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Una formación de otro perfil no se puede quitar por la ruta de este y sigue en su perfil")
    void removeEducation_shouldKeepOtherProfileEducation_whenIdBelongsToAnotherProfile() throws Exception {
        var uidA = newUid();
        var uidB = newUid();
        var profileA = api.createProfile(uidA);
        var profileB = api.createProfile(uidB);
        var educationOfB = api.json(addEducation(uidB, profileB, EDUCATION_ENDED)).get("educations").get(0).get("id").asString();

        var response = api.send("DELETE", "/api/v1/profiles/" + profileA + "/educations/" + educationOfB, uidA, null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(api.json(response).get("code").asString()).isEqualTo("EDUCATION_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM educacion WHERE perfil_id = ?::uuid", Integer.class, profileB))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Quien no es el dueño recibe 403 al agregar y al quitar una formación, y el perfil no cambia")
    void education_shouldNotChangeOtherProfile_whenUserIsNotOwner() throws Exception {
        var owner = newUid();
        var intruder = newUid();
        var profileId = api.createProfile(owner);
        var educationId = api.json(addEducation(owner, profileId, EDUCATION_ENDED)).get("educations").get(0).get("id").asString();

        var post = addEducation(intruder, profileId, EDUCATION_BASE + "}");
        var delete = api.send("DELETE", "/api/v1/profiles/" + profileId + "/educations/" + educationId, intruder, null);

        assertThat(post.statusCode()).isEqualTo(403);
        assertThat(delete.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM educacion WHERE perfil_id = ?::uuid", Integer.class, profileId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("El área de estudio salió del contrato: si llega se ignora y la respuesta no la tiene")
    void addEducation_shouldIgnoreFieldOfStudy_whenSent() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = addEducation(uid, profileId, EDUCATION_BASE + ",\"fieldOfStudy\":\"Sistemas\",\"provenance\":\"AI_SUGGESTED\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        var education = api.json(response).get("educations").get(0);
        assertThat(education.has("fieldOfStudy")).isFalse();
        assertThat(education.get("provenance").asString()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Un año suelto como inicio responde 422 con el formato esperado y no 500")
    void addEducation_shouldReturn422_whenStartDateIsOnlyYear() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = addEducation(uid, profileId,
                "{\"institution\":\"Unicauca\",\"degree\":\"Sistemas\",\"level\":\"TECHNICAL\",\"startDate\":\"2024\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("errors").get(0).get("code").asString()).isEqualTo("START_DATE_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Un carácter de control en la institución responde 422 y no 500")
    void addEducation_shouldRejectControlCharacter_whenInstitutionHasNul() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);

        var response = addEducation(uid, profileId,
                "{\"institution\":\"Uni\\u0000\",\"degree\":\"Sistemas\",\"level\":\"TECHNICAL\",\"startDate\":\"2024-01\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(api.json(response).get("errors").get(0).get("code").asString()).isEqualTo("INSTITUTION_INVALID_CHARACTERS");
    }

    @Test
    @DisplayName("Repetir veinte veces la eliminación de una formación no tiene más efecto que la primera")
    void removeEducation_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes() throws Exception {
        var uid = newUid();
        var profileId = api.createProfile(uid);
        addEducation(uid, profileId, EDUCATION_BASE + "}");
        addEducation(uid, profileId, EDUCATION_BASE + "}");
        var first = api.json(addEducation(uid, profileId, EDUCATION_BASE + "}")).get("educations").get(0).get("id").asString();
        var path = "/api/v1/profiles/" + profileId + "/educations/" + first;

        assertThat(api.send("DELETE", path, uid, null).statusCode()).isEqualTo(204);
        for (int i = 0; i < 19; i++) {
            var repeated = api.send("DELETE", path, uid, null);
            assertThat(repeated.statusCode()).isEqualTo(404);
            assertThat(api.json(repeated).get("code").asString()).isEqualTo("EDUCATION_NOT_FOUND");
        }

        var profile = api.json(api.send("GET", "/api/v1/profiles/" + profileId, uid, null));
        assertThat(profile.get("educations")).hasSize(2);
        assertThat(profile.get("status").asString()).isEqualTo("IN_PROGRESS");
    }

    private HttpResponse<String> addEducation(String uid, String profileId, String body) throws Exception {
        return api.send("POST", "/api/v1/profiles/" + profileId + "/educations", uid, body);
    }

    private HttpResponse<String> add(String uid, String profileId, String body) throws Exception {
        return api.send("POST", "/api/v1/profiles/" + profileId + "/work-experiences", uid, body);
    }

    /** Agrega lo que pide finalizar (nombre, resumen, formación, habilidad y un rol objetivo) y finaliza el perfil. */
    private void completeProfile(String uid, String profileId) throws Exception {
        var base = "/api/v1/profiles/" + profileId;
        assertThat(api.send("PATCH", base, uid, "{\"name\":\"Perfil E2E\",\"summary\":\"Resumen E2E de una persona desarrolladora.\"}")
                .statusCode()).isEqualTo(200);
        assertThat(addEducation(uid, profileId, EDUCATION_ENDED).statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/skills", uid,
                "{\"skillName\":\"Java\",\"level\":\"ADVANCED\",\"provenance\":\"MANUAL\"}").statusCode()).isEqualTo(201);
        assertThat(api.send("POST", base + "/target-roles", uid,
                "{\"professionalRoleId\":\"a0000001-0000-0000-0000-000000000002\"}").statusCode()).isEqualTo(201);
        var completion = api.send("POST", base + "/completion", uid, null);
        assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
    }

    private static String newUid() {
        return "uid-work-" + UUID.randomUUID();
    }
}
