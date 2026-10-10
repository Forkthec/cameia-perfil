package co.edu.unicauca.cameia.perfil.presentation;

import org.junit.jupiter.api.AfterEach;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mes actual sale del reloj en UTC y no de la hora local: a la 01:00 UTC del 1 de noviembre ya es noviembre,
 * aunque en Colombia todavía sea la noche del 31 de octubre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WorkHistoryNovemberUtcIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Reloj fijo a la 01:00 UTC del 1 de noviembre de 2026. */
    @TestConfiguration
    static class NovemberClockConfig {
        @Bean
        @Primary
        Clock novemberClock() {
            return Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-nov-%'");
    }

    @Test
    @DisplayName("Un inicio en noviembre se acepta cuando el reloj UTC ya marca noviembre")
    void addWorkExperience_shouldAcceptNovember_whenUtcIsAlreadyNovember() throws Exception {
        var api = new ApiTestClient(port);
        var uid = "uid-nov-" + UUID.randomUUID();
        var profileId = api.createProfile(uid);

        var response = api.send("POST", "/api/v1/profiles/" + profileId + "/work-experiences", uid,
                "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\",\"startDate\":\"2026-11\"}");

        assertThat(response.statusCode()).isEqualTo(201);
    }
}
