package co.edu.unicauca.cameia.perfil.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Forma común de toda respuesta de error de Perfil ({@code application/problem+json}, RFC 9457).
 *
 * <p>Solo documenta el contrato en OpenAPI: el manejador de errores arma la respuesta con
 * {@code ProblemDetail} y estos mismos campos.</p>
 *
 * @param type                tipo del problema; {@code about:blank} en todos los casos
 * @param title               título corto del problema
 * @param status              estado HTTP
 * @param detail              mensaje para la persona; nunca repite el valor recibido
 * @param instance            ruta de la petición
 * @param code                código estable de la causa
 * @param requestId           identificador de la petición, igual al encabezado {@code X-Request-Id}
 * @param errors              campos rechazados; solo con {@code VALIDATION_FAILED}
 * @param missingRequirements requisitos que faltan; solo con {@code PROFILE_INCOMPLETE}
 */
@Schema(name = "ApiError", description = "Respuesta de error común de Perfil (application/problem+json)",
        example = """
                {
                  "type": "about:blank",
                  "title": "Datos no válidos",
                  "status": 422,
                  "detail": "Revisa los campos marcados.",
                  "instance": "/api/v1/profiles/3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10/skills",
                  "code": "VALIDATION_FAILED",
                  "requestId": "9b2d7c4e-1f3a-4e8b-a6d5-0c7e2f9a1b34",
                  "errors": [
                    {"field": "level", "code": "SKILL_LEVEL_REQUIRED", "message": "Elige un nivel."}
                  ]
                }""")
public record ApiErrorResponse(
        @Schema(example = "about:blank") String type,
        @Schema(example = "Datos no válidos") String title,
        @Schema(example = "422") int status,
        @Schema(example = "Revisa los campos marcados.") String detail,
        @Schema(example = "/api/v1/profiles/3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10/skills") String instance,
        @Schema(description = "Código estable de la causa, con la forma SUJETO_CAUSA", example = "VALIDATION_FAILED")
        String code,
        @Schema(example = "9b2d7c4e-1f3a-4e8b-a6d5-0c7e2f9a1b34") String requestId,
        @ArraySchema(schema = @Schema(implementation = FieldError.class),
                arraySchema = @Schema(description = "Solo con VALIDATION_FAILED: un elemento por campo rechazado"))
        List<FieldError> errors,
        @ArraySchema(schema = @Schema(example = "summary"),
                arraySchema = @Schema(description = "Solo con PROFILE_INCOMPLETE: requisitos que faltan para finalizar"))
        List<String> missingRequirements) {

    /**
     * Un campo rechazado por la validación.
     *
     * @param field   nombre del campo en el cuerpo
     * @param code    código del campo
     * @param message mensaje para la persona
     */
    @Schema(name = "ApiFieldError", description = "Campo rechazado por la validación")
    public record FieldError(
            @Schema(example = "level") String field,
            @Schema(example = "SKILL_LEVEL_REQUIRED") String code,
            @Schema(example = "Elige un nivel.") String message) { }
}
