package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cupo de Perfiles Profesionales del Plan Free, con el servidor real en puerto aleatorio y PostgreSQL real:
 * quien ya tiene perfil, en cualquier estado, recibe 403 {@code PLAN_LIMIT} y no se crea nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlanLimitIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PLAN_LIMIT_DETAIL = "Tu Plan Free permite 1 Perfil Profesional.";

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-plan-%'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "COMPLETED"})
    @DisplayName("Un Usuario del Plan Free con un perfil, en Borrador o activo, recibe 403 PLAN_LIMIT")
    void createProfile_shouldReturn403_whenFreeUserHasOneProfile(String status) throws Exception {
        assertThat(createProfile("uid-plan-0").statusCode()).isEqualTo(201);
        jdbc.update("UPDATE perfil_profesional SET estado = ? WHERE firebase_uid = 'uid-plan-0'", status);

        var response = createProfile("uid-plan-0");

        assertPlanLimit(response);
    }

    @Test
    @DisplayName("Cuando el cupo está alcanzado no se crea ningún perfil más")
    void createProfile_shouldCreateNothing_whenLimitIsReached() throws Exception {
        createProfile("uid-plan-1");

        createProfile("uid-plan-1");

        assertThat(countProfiles("uid-plan-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-2.12.18: quien conservó tres perfiles recibe 403 y ninguno cambia de estado")
    void createProfile_shouldReturn403_whenFreeUserKeptThreeProfiles() throws Exception {
        createProfile("uid-plan-2");
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO perfil_profesional (id, firebase_uid, creado_en, actualizado_en) "
                    + "VALUES (?, 'uid-plan-2', now(), now())", UUID.randomUUID());
        }
        jdbc.update("UPDATE perfil_profesional SET estado = 'COMPLETED' WHERE firebase_uid = 'uid-plan-2'");

        var response = createProfile("uid-plan-2");

        assertPlanLimit(response);
        assertThat(countProfiles("uid-plan-2")).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM perfil_profesional WHERE firebase_uid = 'uid-plan-2' AND estado = 'COMPLETED'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("Veinte peticiones seguidas tras el primer perfil responden 403 y el Usuario sigue con uno")
    void createProfile_shouldStayAtOne_whenRepeatedTwentyTimes() throws Exception {
        assertThat(createProfile("uid-plan-3").statusCode()).isEqualTo(201);

        for (int i = 0; i < 20; i++) {
            assertThat(createProfile("uid-plan-3").statusCode()).isEqualTo(403);
        }

        assertThat(countProfiles("uid-plan-3")).isEqualTo(1);
    }

    private void assertPlanLimit(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        var contentType = response.headers().firstValue("Content-Type").orElse("");
        assertThat(contentType).contains("application/problem+json").containsIgnoringCase("charset=UTF-8");
        assertThat(response.body()).contains("\"code\":\"PLAN_LIMIT\"").contains("\"detail\":\"" + PLAN_LIMIT_DETAIL + "\"");
    }

    private int countProfiles(String uid) {
        return jdbc.queryForObject("SELECT count(*) FROM perfil_profesional WHERE firebase_uid = ?", Integer.class, uid);
    }

    private HttpResponse<String> createProfile(String uid) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/profiles"))
                .header("X-User-Id", uid).POST(HttpRequest.BodyPublishers.noBody()).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
