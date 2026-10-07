package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Base de datos PostgreSQL real para las pruebas de integración, levantada por Testcontainers. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    /** @return contenedor de PostgreSQL con la misma imagen que usa docker-compose */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
