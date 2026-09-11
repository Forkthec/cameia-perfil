package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.service.ProfessionalRoleAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfessionalRoleResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de roles profesionales TI (CM-23). Solo lectura. */
@RestController
@RequestMapping("/api/v1/professional-roles")
class ProfessionalRoleController {

    private final ProfessionalRoleAppService service;
    ProfessionalRoleController(ProfessionalRoleAppService service) { this.service = service; }

    @GetMapping
    ResponseEntity<List<ProfessionalRoleResponse>> listAll() {
        return ResponseEntity.ok(service.listAll().stream().map(ProfessionalRoleResponse::from).toList());
    }
}
