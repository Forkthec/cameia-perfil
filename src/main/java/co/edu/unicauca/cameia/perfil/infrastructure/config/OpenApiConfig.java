package co.edu.unicauca.cameia.perfil.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del contrato OpenAPI que expone este microservicio.
 *
 * <p>Responde a {@code API-TBD-14}, que advierte que sin esquemas publicados "Backend, Gateway y
 * Web no pueden integrarse de manera independiente". Springdoc genera la documentación desde el
 * código, así que aquí solo se declara la portada: las operaciones aparecerán conforme cada
 * Historia de Usuario agregue su controlador.
 *
 * <p>Rutas resultantes: {@code /swagger-ui.html} y {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cameiaPerfilOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CAMEIA — Microservicio de Perfil Profesional")
                .version("v1")
                .description("""
                        Perfiles profesionales, experiencia, educación, habilidades y roles \
                        objetivo del MVP de CAMEIA, con procedencia y revisión humana de todo \
                        dato asistido por IA.

                        Las operaciones se publican a medida que cada Historia de Usuario \
                        agrega su controlador. Las decisiones contractuales abiertas \
                        (API-TBD-05, 06, 07, 09 y 18) no se cierran en este documento.""")
                .contact(new Contact().name("Equipo CAMEIA — Grupo 4, Universidad del Cauca")));
    }
}
