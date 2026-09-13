package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddEducationRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddSkillRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddTargetRoleRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.CompletionErrorResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateTargetRoleRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddWorkExperienceRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfileResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateProfileInfoRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateSalaryExpectationRequest;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints del perfil profesional (CM-16 a CM-24). */
@Tag(name = "Perfiles", description = "Gestión del perfil profesional del candidato")
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private final ProfileAppService profileAppService;
    ProfileController(ProfileAppService profileAppService) { this.profileAppService = profileAppService; }

    @Operation(summary = "Crear perfil profesional")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil creado exitosamente",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un perfil con ese Firebase UID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    ResponseEntity<ProfileResponse> createProfile(
            @Parameter(description = "Firebase UID del usuario autenticado", required = true)
            @RequestHeader("X-User-Id") String uid) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.createProfile(new CreateProfileCommand(uid))));
    }

    @Operation(summary = "Obtener perfil profesional por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    ResponseEntity<ProfileResponse> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.getProfile(id)));
    }

    @Operation(summary = "Actualizar información básica del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos en el cuerpo de la solicitud",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    ResponseEntity<ProfileResponse> updateProfileInfo(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateProfileInfoRequest r) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateProfileInfo(
                new UpdateProfileInfoCommand(id, r.name(), r.summary(), r.preferredModality(), r.provenance()))));
    }

    @Operation(summary = "Agregar experiencia laboral al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Experiencia laboral agregada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos en el cuerpo de la solicitud",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Datos de fechas inconsistentes (ej: endDate anterior a startDate)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/work-experiences")
    ResponseEntity<ProfileResponse> addWorkExperience(@PathVariable UUID id,
                                                      @Valid @RequestBody AddWorkExperienceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addWorkExperience(
                new AddWorkExperienceCommand(id, r.company(), r.position(), r.description(),
                        r.startDate(), r.endDate(), r.employmentStatus(), r.provenance()))));
    }

    @Operation(summary = "Eliminar experiencia laboral del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Experiencia laboral eliminada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil o experiencia no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}/work-experiences/{expId}")
    ResponseEntity<ProfileResponse> removeWorkExperience(@PathVariable UUID id, @PathVariable UUID expId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeWorkExperience(id, expId)));
    }

    @Operation(summary = "Agregar educación al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Educación agregada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos en el cuerpo de la solicitud",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/educations")
    ResponseEntity<ProfileResponse> addEducation(@PathVariable UUID id,
                                                 @Valid @RequestBody AddEducationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addEducation(
                new AddEducationCommand(id, r.institution(), r.degree(), r.fieldOfStudy(),
                        r.level(), r.startDate(), r.endDate(), r.inProgress(), r.provenance()))));
    }

    @Operation(summary = "Eliminar educación del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Educación eliminada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil o educación no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}/educations/{eduId}")
    ResponseEntity<ProfileResponse> removeEducation(@PathVariable UUID id, @PathVariable UUID eduId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeEducation(id, eduId)));
    }

    /** CM-24: oculto en Swagger (fuera del MVP). */
    @Hidden
    @PatchMapping("/{id}/salary-expectation")
    ResponseEntity<ProfileResponse> updateSalaryExpectation(@PathVariable UUID id,
                                                            @RequestBody UpdateSalaryExpectationRequest r) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateSalaryExpectation(
                new UpdateSalaryExpectationCommand(id, r.amount()))));
    }

    @Operation(summary = "Agregar habilidad al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Habilidad agregada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos en el cuerpo de la solicitud",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/skills")
    ResponseEntity<ProfileResponse> addSkill(@PathVariable UUID id,
                                             @Valid @RequestBody AddSkillRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addSkill(
                new AddSkillCommand(id, r.skillName(), r.level(), r.provenance()))));
    }

    @Operation(summary = "Eliminar habilidad del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Habilidad eliminada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil o habilidad no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}/skills/{skillId}")
    ResponseEntity<ProfileResponse> removeSkill(@PathVariable UUID id, @PathVariable UUID skillId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeSkill(id, skillId)));
    }

    @Operation(summary = "Solicitar revisión del perfil",
            description = "Cambia el estado del perfil de IN_PROGRESS a IN_REVIEW si cumple los 5 requisitos. " +
                    "Si el perfil está incompleto, devuelve 422 con la lista de campos faltantes en missingRequirements.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Revisión solicitada; estado cambia a IN_REVIEW",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Perfil incompleto; el cuerpo contiene la lista de requisitos faltantes",
                    content = @Content(schema = @Schema(implementation = CompletionErrorResponse.class)))
    })
    @PostMapping("/{id}/review-requests")
    ResponseEntity<ProfileResponse> requestReview(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.requestReview(id)));
    }

    @Operation(summary = "Agregar rol objetivo al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rol objetivo agregado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos en el cuerpo de la solicitud",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Perfil o rol profesional no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe ese rol objetivo en el perfil",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Se alcanzó el máximo de roles objetivo permitidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/target-roles")
    ResponseEntity<ProfileResponse> addTargetRole(@PathVariable UUID id,
                                                  @Valid @RequestBody AddTargetRoleRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addTargetRole(
                new AddTargetRoleCommand(id, r.professionalRoleId(), r.provenance()))));
    }

    @Operation(summary = "Actualizar rol objetivo del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol objetivo actualizado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos en el cuerpo de la solicitud",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Perfil, rol objetivo o rol profesional no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe ese rol objetivo en el perfil",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/target-roles/{roleId}")
    ResponseEntity<ProfileResponse> updateTargetRole(@PathVariable UUID id, @PathVariable UUID roleId,
                                                     @Valid @RequestBody UpdateTargetRoleRequest r) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateTargetRole(
                new UpdateTargetRoleCommand(id, roleId, r.professionalRoleId()))));
    }

    @Operation(summary = "Eliminar rol objetivo del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol objetivo eliminado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil o rol objetivo no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "No se puede eliminar el único rol objetivo de un perfil COMPLETED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}/target-roles/{roleId}")
    ResponseEntity<ProfileResponse> removeTargetRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeTargetRole(id, roleId)));
    }

    @Operation(summary = "Marcar perfil como COMPLETED",
            description = "Valida que el perfil cumpla los 5 requisitos (nombre, resumen, ≥1 educación, ≥1 habilidad, ≥1 rol objetivo) " +
                    "y cambia su estado a COMPLETED. Si no los cumple devuelve 422 con la lista de campos faltantes en missingRequirements.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil marcado como COMPLETED",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El perfil ya está en estado COMPLETED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "El perfil no cumple los 5 requisitos; el cuerpo contiene la lista de campos faltantes",
                    content = @Content(schema = @Schema(implementation = CompletionErrorResponse.class)))
    })
    @PostMapping("/{id}/completion")
    ResponseEntity<ProfileResponse> completeProfile(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.completeProfile(id)));
    }
}
