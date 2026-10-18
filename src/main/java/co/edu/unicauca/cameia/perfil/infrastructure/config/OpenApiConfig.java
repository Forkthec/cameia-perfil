package co.edu.unicauca.cameia.perfil.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
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

    /**
     * Documenta en todas las operaciones los rechazos de protocolo que ocurren antes de la lógica de cada ruta:
     * 406 (el {@code Accept} no se puede cumplir o no se interpreta), 415 (tipo de contenido no admitido, formulario
     * mal codificado o multipart ilegible) y 413 (multipart sobre el tope). Así ninguna operación depende de que se
     * recuerde repetir las tres respuestas.
     *
     * @return el personalizador que agrega las tres respuestas, con la forma común de error de Perfil
     */
    @Bean
    OperationCustomizer protocolErrorResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("406", protocolError(
                    "MEDIA_TYPE_NOT_ACCEPTABLE: el cliente solo acepta un formato distinto de JSON, "
                            + "o su Accept no se puede interpretar",
                    "formatoNoDisponible", 406, "Tipo de respuesta no admitido", "Tipo de respuesta no admitido.",
                    "MEDIA_TYPE_NOT_ACCEPTABLE"));
            operation.getResponses().addApiResponse("415", protocolError(
                    "MEDIA_TYPE_NOT_ALLOWED: el tipo de contenido no se admite, o es un formulario mal codificado. "
                            + "REQUEST_MULTIPART_INVALID_FORMAT: un multipart/* que no se pudo leer, por ejemplo sin boundary",
                    "tipoNoAdmitido", 415, "Tipo de contenido no admitido", "Tipo de contenido no admitido.",
                    "MEDIA_TYPE_NOT_ALLOWED"));
            operation.getResponses().addApiResponse("413", protocolError(
                    "REQUEST_BODY_TOO_LARGE: un multipart supera el tamaño admitido (1 MiB por parte y 10 MiB por petición)",
                    "cuerpoDemasiadoGrande", 413, "Cuerpo demasiado grande", "Cuerpo de la petición demasiado grande.",
                    "REQUEST_BODY_TOO_LARGE"));
            return operation;
        };
    }

    private static ApiResponse protocolError(String description, String exampleName, int status, String title,
            String detail, String code) {
        var example = new Example().value(java.util.Map.of("type", "about:blank", "title", title, "status", status,
                "detail", detail, "code", code, "requestId", "9b2d7c4e-1f3a-4e8b-a6d5-0c7e2f9a1b34"));
        var media = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))
                .addExamples(exampleName, example);
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", media));
    }
}
