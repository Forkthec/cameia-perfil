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
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddEducationRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.ApiErrorResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddSkillRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddTargetRoleRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateTargetRoleRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddWorkExperienceRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfileResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateProfileInfoRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateSalaryExpectationRequest;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/** Endpoints del perfil profesional del Usuario. */
@Tag(name = "Perfiles", description = "Gestión del perfil profesional del candidato")
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private static final String EMPTY_PROFILE_EXAMPLE = """
            {
              "id": "3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10",
              "status": "IN_PROGRESS",
              "reviewStatus": "PENDING_REVIEW",
              "provenance": "MANUAL",
              "name": null,
              "summary": null,
              "salaryExpectation": null,
              "preferredModality": null,
              "createdAt": "2026-10-07T15:04:05Z",
              "updatedAt": "2026-10-07T15:04:05Z",
              "targetRoles": [],
              "workExperiences": [],
              "educations": [],
              "profileSkills": []
            }""";

    private static final String PROFILE_LIMIT_REACHED_EXAMPLE = """
            {
              "type": "about:blank",
              "title": "Cupo del plan alcanzado",
              "status": 409,
              "detail": "Tu Plan Free permite 1 Perfil Profesional.",
              "code": "PROFILE_LIMIT_REACHED",
              "requestId": "3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10"
            }""";

    private final ProfileAppService profileAppService;
    private final ProfileCreationAppService profileCreationAppService;

    ProfileController(ProfileAppService profileAppService, ProfileCreationAppService profileCreationAppService) {
        this.profileAppService = profileAppService;
        this.profileCreationAppService = profileCreationAppService;
    }

    @Operation(summary = "Crear perfil profesional")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil vacío creado, en estado IN_PROGRESS. "
                    + "Una petición repetida mientras la creación está en proceso devuelve el mismo perfil.",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class),
                            examples = @ExampleObject(value = EMPTY_PROFILE_EXAMPLE))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El Usuario ya tenía el máximo de perfiles de su plan; el Plan Free permite uno (code PROFILE_LIMIT_REACHED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(value = PROFILE_LIMIT_REACHED_EXAMPLE))),
            @ApiResponse(responseCode = "500", description = "Error interno (code INTERNAL_ERROR); el detalle nunca incluye el mensaje de la excepción",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    ResponseEntity<ProfileResponse> createProfile(
            @Parameter(description = "Firebase UID del usuario autenticado", required = true)
            @RequestHeader("X-User-Id") String uid) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileCreationAppService.createProfile(new CreateProfileCommand(uid))));
    }

    @Operation(summary = "Obtener perfil profesional por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Identificador mal escrito (code PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    ResponseEntity<ProfileResponse> getProfile(@PathVariable UUID id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.getProfile(id, uid)));
    }

    @Operation(summary = "Actualizar información básica del perfil",
            description = "Un campo ausente o nulo no cambia. Un resumen vacío o con solo espacios lo borra, salvo en un perfil activo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "422", description = "Campos inválidos (code VALIDATION_FAILED, con errors[]: PROFILE_NAME_REQUIRED, PROFILE_NAME_TOO_LONG, SUMMARY_NOT_ALLOWED (borrar el resumen de un perfil activo), SUMMARY_TOO_LONG, PREFERRED_MODALITY_INVALID_VALUE, PROVENANCE_INVALID_VALUE). Cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT) o identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    ResponseEntity<ProfileResponse> updateProfileInfo(@PathVariable UUID id,
            @Valid @RequestBody UpdateProfileInfoRequest r,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateProfileInfo(
                new UpdateProfileInfoCommand(id, uid, r.name(), r.summary(), r.preferredModality(), r.provenance()))));
    }

    @Operation(summary = "Agregar experiencia laboral al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Experiencia laboral agregada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Campos inválidos (code VALIDATION_FAILED, con errors[]: COMPANY_REQUIRED, COMPANY_TOO_LONG, POSITION_REQUIRED, POSITION_TOO_LONG, START_DATE_REQUIRED, START_DATE_INVALID_FORMAT, END_DATE_INVALID_FORMAT, END_DATE_REQUIRED, END_DATE_NOT_ALLOWED, END_DATE_BEFORE_START_DATE, EMPLOYMENT_STATUS_REQUIRED, EMPLOYMENT_STATUS_INVALID_VALUE, PROVENANCE_REQUIRED, PROVENANCE_INVALID_VALUE). Cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT) o identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/work-experiences")
    ResponseEntity<ProfileResponse> addWorkExperience(@PathVariable UUID id,
            @Valid @RequestBody AddWorkExperienceRequest r,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addWorkExperience(
                new AddWorkExperienceCommand(id, uid, r.company(), r.position(), r.description(),
                        r.startDate(), r.endDate(), r.employmentStatus(), r.provenance()))));
    }

    @Operation(summary = "Eliminar experiencia laboral del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Experiencia laboral eliminada, sin cuerpo"),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND) o experiencia que no es de este perfil (code WORK_EXPERIENCE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Identificador mal escrito (code PROFILE_ID_INVALID_FORMAT o WORK_EXPERIENCE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}/work-experiences/{expId}")
    ResponseEntity<Void> removeWorkExperience(@PathVariable UUID id, @PathVariable UUID expId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        profileAppService.removeWorkExperience(id, uid, expId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Agregar educación al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Educación agregada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "422", description = "Campos inválidos (code VALIDATION_FAILED, con errors[]: INSTITUTION_REQUIRED, INSTITUTION_TOO_LONG, DEGREE_REQUIRED, DEGREE_TOO_LONG, EDUCATION_LEVEL_REQUIRED, EDUCATION_LEVEL_INVALID_VALUE, START_DATE_REQUIRED, START_DATE_INVALID_FORMAT, END_DATE_INVALID_FORMAT, END_DATE_NOT_ALLOWED, PROVENANCE_REQUIRED, PROVENANCE_INVALID_VALUE). Cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT) o identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/educations")
    ResponseEntity<ProfileResponse> addEducation(@PathVariable UUID id,
            @Valid @RequestBody AddEducationRequest r,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addEducation(
                new AddEducationCommand(id, uid, r.institution(), r.degree(), r.fieldOfStudy(),
                        r.level(), r.startDate(), r.endDate(), Boolean.TRUE.equals(r.inProgress()), r.provenance()))));
    }

    @Operation(summary = "Eliminar educación del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Educación eliminada, sin cuerpo"),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND) o formación que no es de este perfil (code EDUCATION_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Identificador mal escrito (code PROFILE_ID_INVALID_FORMAT o EDUCATION_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}/educations/{eduId}")
    ResponseEntity<Void> removeEducation(@PathVariable UUID id, @PathVariable UUID eduId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        profileAppService.removeEducation(id, uid, eduId);
        return ResponseEntity.noContent().build();
    }

    /** Oculto en Swagger: queda fuera del alcance del MVP. */
    @Hidden
    @PatchMapping("/{id}/salary-expectation")
    ResponseEntity<ProfileResponse> updateSalaryExpectation(@PathVariable UUID id,
            @Valid @RequestBody UpdateSalaryExpectationRequest r,
            @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateSalaryExpectation(
                new UpdateSalaryExpectationCommand(id, uid, r.amount()))));
    }

    @Operation(summary = "Agregar habilidad al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Habilidad agregada",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "422", description = "Campos inválidos (code VALIDATION_FAILED, con errors[]: SKILL_NAME_REQUIRED, SKILL_NAME_TOO_LONG, SKILL_LEVEL_REQUIRED, SKILL_LEVEL_INVALID_VALUE, PROVENANCE_REQUIRED, PROVENANCE_INVALID_VALUE). Cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT) o identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "La habilidad ya está en el perfil, sin distinguir mayúsculas ni espacios (code SKILL_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/skills")
    ResponseEntity<ProfileResponse> addSkill(@PathVariable UUID id,
            @Valid @RequestBody AddSkillRequest r,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addSkill(
                new AddSkillCommand(id, uid, r.skillName(), r.level(), r.provenance()))));
    }

    @Operation(summary = "Eliminar habilidad del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Habilidad eliminada, sin cuerpo"),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND) o habilidad que no es de este perfil (code SKILL_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Identificador mal escrito (code PROFILE_ID_INVALID_FORMAT o SKILL_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}/skills/{skillId}")
    ResponseEntity<Void> removeSkill(@PathVariable UUID id, @PathVariable UUID skillId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        profileAppService.removeSkill(id, uid, skillId);
        return ResponseEntity.noContent().build();
    }

    /** Oculto en Swagger: no tiene HU en el MVP y el estado IN_REVIEW no es alcanzable (hallazgo I-024 del backlog). */
    @Hidden
    @PostMapping("/{id}/review-requests")
    ResponseEntity<ProfileResponse> requestReview(@PathVariable UUID id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.requestReview(id, uid)));
    }

    @Operation(summary = "Agregar rol objetivo al perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rol objetivo agregado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND) o rol profesional inexistente en el catálogo (code PROFESSIONAL_ROLE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe ese rol objetivo en el perfil (code TARGET_ROLE_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Campos inválidos (code VALIDATION_FAILED, con errors[]: PROFESSIONAL_ROLE_ID_REQUIRED, PROVENANCE_REQUIRED, PROVENANCE_INVALID_VALUE), cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT), máximo de roles objetivo alcanzado (TARGET_ROLE_LIMIT_REACHED) o identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/target-roles")
    ResponseEntity<ProfileResponse> addTargetRole(@PathVariable UUID id,
            @Valid @RequestBody AddTargetRoleRequest r,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addTargetRole(
                new AddTargetRoleCommand(id, uid, r.professionalRoleId(), r.provenance()))));
    }

    @Operation(summary = "Actualizar rol objetivo del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol objetivo actualizado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "422", description = "Campos inválidos (code VALIDATION_FAILED, con errors[]: PROFESSIONAL_ROLE_ID_REQUIRED), cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT) o identificador mal escrito (PROFILE_ID_INVALID_FORMAT, TARGET_ROLE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND), rol objetivo inexistente en el perfil (code TARGET_ROLE_NOT_FOUND) o rol profesional inexistente en el catálogo (code PROFESSIONAL_ROLE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe ese rol objetivo en el perfil (code TARGET_ROLE_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}/target-roles/{roleId}")
    ResponseEntity<ProfileResponse> updateTargetRole(@PathVariable UUID id, @PathVariable UUID roleId,
            @Valid @RequestBody UpdateTargetRoleRequest r,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateTargetRole(
                new UpdateTargetRoleCommand(id, uid, roleId, r.professionalRoleId()))));
    }

    @Operation(summary = "Eliminar rol objetivo del perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rol objetivo eliminado, sin cuerpo"),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND) o rol objetivo que no es de este perfil (code TARGET_ROLE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "No se puede eliminar el único rol objetivo de un perfil COMPLETED (code TARGET_ROLE_NOT_ALLOWED) o identificador mal escrito (PROFILE_ID_INVALID_FORMAT, TARGET_ROLE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}/target-roles/{roleId}")
    ResponseEntity<Void> removeTargetRole(@PathVariable UUID id, @PathVariable UUID roleId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        profileAppService.removeTargetRole(id, uid, roleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marcar perfil como COMPLETED",
            description = "Valida que el perfil cumpla los 5 requisitos (nombre, resumen, ≥1 educación, ≥1 habilidad, ≥1 rol objetivo) " +
                    "y cambia su estado a COMPLETED. Si no los cumple devuelve 422 con la lista de campos faltantes en missingRequirements.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil marcado como COMPLETED",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Identidad ausente, en blanco o de más de 128 caracteres (code IDENTITY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El perfil pertenece a otro Usuario (code PROFILE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado (code PROFILE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El perfil ya está activo (code PROFILE_ALREADY_COMPLETED)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "El perfil no cumple los requisitos para finalizar (code PROFILE_INCOMPLETE, con missingRequirements[]) o identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/completion")
    ResponseEntity<ProfileResponse> completeProfile(@PathVariable UUID id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.completeProfile(id, uid)));
    }
}
