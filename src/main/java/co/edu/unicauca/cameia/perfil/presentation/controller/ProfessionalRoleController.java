package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.service.ProfessionalRoleAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfessionalRoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de roles profesionales TI (CM-23). Solo lectura. */
@Tag(name = "Roles Profesionales", description = "Catálogo de roles TI disponibles para usar como rol objetivo")
@RestController
@RequestMapping("/api/v1/professional-roles")
class ProfessionalRoleController {

    private final ProfessionalRoleAppService service;
    ProfessionalRoleController(ProfessionalRoleAppService service) { this.service = service; }

    @Operation(summary = "Listar catálogo de roles profesionales TI")
    @ApiResponse(responseCode = "200", description = "Lista de roles profesionales",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProfessionalRoleResponse.class))))
    @GetMapping
    ResponseEntity<List<ProfessionalRoleResponse>> listAll() {
        return ResponseEntity.ok(service.listAll().stream().map(ProfessionalRoleResponse::from).toList());
    }
}
