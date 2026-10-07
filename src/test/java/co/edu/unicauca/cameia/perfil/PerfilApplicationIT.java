package co.edu.unicauca.cameia.perfil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Prueba de humo del arranque.
 *
 * <p>Si el {@code pom.xml}, el {@code application.yml}, la conexión a PostgreSQL o Flyway están
 * mal, falla aquí y no en producción. En una tarea de estructura no hay lógica de negocio que
 * probar, y eso se dice con honestidad en vez de inventar pruebas vacías: lo que sí corresponde
 * es dejar montada la infraestructura de pruebas y en verde.
 *
 * <p>Necesita una base de datos, porque {@code spring-boot-starter-data-jpa} construye el
 * {@code DataSource} al levantar el contexto y Flyway se conecta al arrancar. Testcontainers
 * levanta un PostgreSQL real para la prueba: basta con tener Docker encendido.
 */
@SpringBootTest
@Testcontainers
class PerfilApplicationIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("El contexto de Spring levanta con la configuración del repositorio")
    void contextLoads() {
        // Sin aserciones a propósito: si el contexto no levanta, @SpringBootTest falla solo.
    }
}
