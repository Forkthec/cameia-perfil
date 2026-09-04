package co.edu.unicauca.cameia.perfil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de humo del arranque.
 *
 * <p>Si el {@code pom.xml}, el {@code application.yml}, la conexión a PostgreSQL o Flyway están
 * mal, falla aquí y no en producción. En una tarea de estructura no hay lógica de negocio que
 * probar, y eso se dice con honestidad en vez de inventar pruebas vacías: lo que sí corresponde
 * es dejar montada la infraestructura de pruebas y en verde.
 *
 * <p>Necesita la base de datos arriba, porque {@code spring-boot-starter-data-jpa} construye el
 * {@code DataSource} al levantar el contexto y Flyway se conecta al arrancar. La forma soportada
 * de ejecutarla sin instalar nada es {@code docker compose run --rm verify}; el README explica
 * las alternativas.
 */
@SpringBootTest
class PerfilApplicationTest {

    @Test
    @DisplayName("El contexto de Spring levanta con la configuración del repositorio")
    void contextLoads() {
        // Sin aserciones a propósito: si el contexto no levanta, @SpringBootTest falla solo.
    }
}
