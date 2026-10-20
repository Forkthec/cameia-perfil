package co.edu.unicauca.cameia.perfil.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba la configuración efectiva del pool de conexiones. */
@SpringBootTest
@Testcontainers
class DatabasePoolConfigurationIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("El pool espera como máximo 5 segundos por una conexión")
    void connectionTimeout_shouldBeFiveSeconds() {
        assertThat(((HikariDataSource) dataSource).getConnectionTimeout()).isEqualTo(5000L);
    }
}
