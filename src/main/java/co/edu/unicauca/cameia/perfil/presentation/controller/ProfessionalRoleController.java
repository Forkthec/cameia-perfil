package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.service.ProfessionalRoleAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfessionalRoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Catálogo de roles profesionales TI (CM-82). Solo lectura, sin autenticación. */
@Tag(name = "Roles Profesionales", description = "Catálogo de roles TI disponibles para usar como rol objetivo")
@RestController
@RequestMapping("/api/v1/profiles/professional-roles")
class ProfessionalRoleController {

    private static final Set<String> SUPPORTED_LANGS = Set.of("es", "en");

    private final ProfessionalRoleAppService service;
    ProfessionalRoleController(ProfessionalRoleAppService service) { this.service = service; }

    @Operation(summary = "Listar catálogo de roles profesionales TI")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de roles profesionales",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProfessionalRoleResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Idioma no soportado (use 'es' o 'en')",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    ResponseEntity<?> listAll(
            @Parameter(description = "Idioma de la respuesta: 'es' (español) o 'en' (inglés). Por defecto 'es'.")
            @RequestParam(value = "lang", defaultValue = "es") String lang) {

        if (!SUPPORTED_LANGS.contains(lang)) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            pd.setTitle("Parámetro inválido");
            pd.setDetail("El valor '" + lang + "' no es un idioma soportado. Use 'es' o 'en'.");
            return ResponseEntity.badRequest().body(pd);
        }

        return ResponseEntity.ok(
                service.listAllByLang(lang).stream().map(ProfessionalRoleResponse::from).toList());
    }
}
