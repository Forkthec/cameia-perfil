package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.presentation.advice.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock ProfileAppService profileAppService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(profileAppService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postProfiles_returns201WithProfileBody() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-001"));
        when(profileAppService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ctrl-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.targetRoles").isArray());
    }

    @Test
    @DisplayName("Un Usuario que ya tiene su perfil recibe 409 con el mensaje del Plan Free y sin datos internos")
    void postProfiles_shouldReturn409WithPlanMessage_whenUserAlreadyHasProfile() throws Exception {
        when(profileAppService.createProfile(any())).thenThrow(new ProfileLimitReachedException());

        var result = mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.title").value("Cupo del plan alcanzado"))
                .andExpect(jsonPath("$.detail").value("Tu Plan Free permite 1 Perfil Profesional."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("TODO").doesNotContain("CM-").doesNotContain("Exception").doesNotContain("co.edu");
    }

    @Test
    @DisplayName("El perfil recién creado es un borrador vacío, sin método de creación")
    void getProfile_shouldReturnEmptyDraft_whenJustCreated() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ana-001"));
        when(profileAppService.getProfile(profile.getId().value(), "uid-ana-001")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/profiles/{id}", profile.getId().value()).header("X-User-Id", "uid-ana-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.targetRoles").isEmpty())
                .andExpect(jsonPath("$.workExperiences").isEmpty())
                .andExpect(jsonPath("$.educations").isEmpty())
                .andExpect(jsonPath("$.profileSkills").isEmpty())
                .andExpect(jsonPath("$.method").doesNotExist());
    }

    @Test
    @DisplayName("Crear un perfil ignora el cuerpo: la identidad sale solo del encabezado")
    void postProfiles_shouldIgnoreBody_whenBodySent() throws Exception {
        when(profileAppService.createProfile(new CreateProfileCommand("uid-ana-001")))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ana-001")));

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"firebaseUid\":\"otro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(profileAppService).createProfile(new CreateProfileCommand("uid-ana-001"));
    }

    @Test
    @DisplayName("Si la base de datos falla, crear un perfil responde el 500 genérico sin detalle")
    void postProfiles_shouldReturnGeneric500_whenDatabaseFails() throws Exception {
        when(profileAppService.createProfile(any())).thenThrow(new DataAccessResourceFailureException("conexión"));

        var result = mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("conexión");
    }

    @Test
    @DisplayName("Sin el encabezado de identidad, crear un perfil responde 401")
    void postProfiles_shouldReturn401_whenXUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Una identidad en blanco responde 401 aunque el encabezado llegue")
    void postProfiles_shouldReturn401_whenXUserIdIsBlank(String uid) throws Exception {
        when(profileAppService.createProfile(new CreateProfileCommand(uid))).thenThrow(new IdentityRequiredException());

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", uid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres crea el perfil")
    void postProfiles_shouldReturn201_whenXUserIdHasMaxLength() throws Exception {
        var uid = "a".repeat(128);
        when(profileAppService.createProfile(new CreateProfileCommand(uid)))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid(uid)));

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", uid))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres responde 401 sin repetir el valor")
    void postProfiles_shouldReturn401WithoutEcho_whenXUserIdIsTooLong() throws Exception {
        var uid = "a".repeat(129);
        when(profileAppService.createProfile(new CreateProfileCommand(uid))).thenThrow(new IdentityRequiredException());

        var result = mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", uid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("aaaa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-13", "31/02/2020"})
    @DisplayName("Una fecha mal escrita responde 422 sin el mensaje del analizador de fechas")
    void addWorkExperience_shouldReturn422_whenStartDateIsMalformed(String startDate) throws Exception {
        when(profileAppService.addWorkExperience(any()))
                .thenAnswer(invocation -> YearMonth.parse(startDate));
        var body = """
                {"company":"ACME","position":"Dev","startDate":"%s","employmentStatus":"CURRENT","provenance":"MANUAL"}
                """.formatted(startDate);

        var result = mockMvc.perform(post("/api/v1/profiles/" + UUID.randomUUID() + "/work-experiences")
                        .header("X-User-Id", "uid-ctrl-date")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Text").doesNotContain("parse");
    }

    static Stream<Arguments> frameworkErrors() {
        var profileId = UUID.randomUUID();
        return Stream.of(
                Arguments.of(get("/api/v1/no-existe"), 404, "ROUTE_NOT_FOUND", "La ruta solicitada no existe."),
                Arguments.of(delete("/api/v1/profiles"), 405, "METHOD_NOT_ALLOWED",
                        "La operación no está permitida en esta ruta."),
                Arguments.of(patch("/api/v1/profiles/" + profileId).header("X-User-Id", "uid-ctrl-415")
                                .contentType(MediaType.TEXT_PLAIN).content("x"),
                        415, "CONTENT_TYPE_NOT_ALLOWED", "Envía los datos en formato JSON."),
                Arguments.of(get("/api/v1/profiles/no-es-uuid").header("X-User-Id", "uid-ctrl-id"),
                        422, "PROFILE_ID_INVALID_FORMAT", "El identificador del perfil no es válido."),
                Arguments.of(delete("/api/v1/profiles/" + profileId + "/skills/xyz").header("X-User-Id", "uid-ctrl-id"),
                        422, "SKILL_ID_INVALID_FORMAT", "El identificador de la habilidad no es válido."));
    }

    @ParameterizedTest
    @MethodSource("frameworkErrors")
    @DisplayName("Los errores del framework responden la forma común con su código, sin repetir la entrada")
    void frameworkError_shouldReturnCommonShape_whenRequestIsRejected(
            MockHttpServletRequestBuilder request, int status, String code, String detail) throws Exception {
        // El detail es el texto fijo del catálogo, así que no puede llevar el valor ni el mensaje de Spring.
        mockMvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("Pedir el perfil en XML responde 406 con su código en JSON")
    void getProfile_shouldReturn406_whenClientAcceptsOnlyXml() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-xml"));
        when(profileAppService.getProfile(profile.getId().value(), "uid-ctrl-xml")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/profiles/{id}", profile.getId().value())
                        .header("X-User-Id", "uid-ctrl-xml").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("ACCEPT_TYPE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value("La respuesta solo está disponible en formato JSON."));
    }

    @Test
    @DisplayName("El 405 conserva el encabezado Allow con los métodos permitidos")
    void deleteProfiles_shouldKeepAllowHeader_whenMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/profiles"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")));
    }

    private static final String WORK_EXPERIENCE = "/work-experiences";
    private static final String SKILLS = "/skills";
    private static final String TARGET_ROLES = "/target-roles";
    private static final String SELECT_OPTION = "Selecciona una opción.";

    /** Cuerpo válido de cada endpoint, en el orden de sus campos. */
    private static Map<String, String> validBody(String endpoint) {
        var body = new LinkedHashMap<String, String>();
        switch (endpoint) {
            case WORK_EXPERIENCE -> {
                body.put("company", "\"ACME\"");
                body.put("position", "\"Dev\"");
                body.put("startDate", "\"2022-01\"");
                body.put("employmentStatus", "\"CURRENT\"");
                body.put("provenance", "\"MANUAL\"");
            }
            case SKILLS -> {
                body.put("skillName", "\"Java\"");
                body.put("level", "\"ADVANCED\"");
                body.put("provenance", "\"MANUAL\"");
            }
            default -> {
                body.put("professionalRoleId", "\"" + UUID.randomUUID() + "\"");
                body.put("provenance", "\"MANUAL\"");
            }
        }
        return body;
    }

    /**
     * Una fila por código de campo: cada {@code NotBlank} se prueba ausente, {@code null}, vacío,
     * con espacios y con un tabulador; cada {@code NotNull}, ausente y {@code null}.
     */
    static Stream<Arguments> fieldCodes() {
        var rows = Stream.of(
                new Object[]{WORK_EXPERIENCE, "company", true, "COMPANY_REQUIRED", "Ingresa la empresa."},
                new Object[]{WORK_EXPERIENCE, "position", true, "POSITION_REQUIRED", "Ingresa el cargo."},
                new Object[]{WORK_EXPERIENCE, "startDate", true, "START_DATE_REQUIRED", "Ingresa la fecha de inicio."},
                new Object[]{WORK_EXPERIENCE, "employmentStatus", false, "EMPLOYMENT_STATUS_REQUIRED", SELECT_OPTION},
                new Object[]{WORK_EXPERIENCE, "provenance", false, "PROVENANCE_REQUIRED", SELECT_OPTION},
                new Object[]{SKILLS, "skillName", true, "SKILL_NAME_REQUIRED", "Ingresa una habilidad."},
                new Object[]{SKILLS, "level", true, "SKILL_LEVEL_REQUIRED", "Elige un nivel."},
                new Object[]{SKILLS, "provenance", true, "PROVENANCE_REQUIRED", SELECT_OPTION},
                new Object[]{TARGET_ROLES, "professionalRoleId", false, "PROFESSIONAL_ROLE_ID_REQUIRED", SELECT_OPTION},
                new Object[]{TARGET_ROLES, "provenance", false, "PROVENANCE_REQUIRED", SELECT_OPTION});
        return rows.flatMap(row -> {
            // null significa que el campo no se envía; el resto son valores JSON literales.
            var values = (boolean) row[2]
                    ? new String[]{null, "null", "\"\"", "\"   \"", "\"\\t\""}
                    : new String[]{null, "null"};
            return Stream.of(values).map(value -> Arguments.of(row[0], row[1], value, row[3], row[4]));
        });
    }

    @ParameterizedTest(name = "{0} {1}={2} -> {3}")
    @MethodSource("fieldCodes")
    @DisplayName("Cada campo obligatorio rechazado responde 422 con su campo, su código y su mensaje")
    void addToProfile_shouldReturnFieldCode_whenRequiredFieldIsMissingOrBlank(
            String endpoint, String field, String value, String code, String message) throws Exception {
        var body = validBody(endpoint);
        if (value == null) {
            body.remove(field);
        } else {
            body.put(field, value);
        }
        var json = body.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));

        mockMvc.perform(post("/api/v1/profiles/" + UUID.randomUUID() + endpoint)
                        .header("X-User-Id", "uid-ctrl-field")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.errors[0].message").value(message));
    }

    @Test
    void patchProfile_returns200WithUpdatedProfile() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-002"));
        profile.updateName(new ProfileName("Ana Sofía"));
        when(profileAppService.updateProfileInfo(any(UpdateProfileInfoCommand.class))).thenReturn(profile);

        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Sofía"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Sofía"));
    }

    @Test
    void getProfile_returns403WhenProfileIsOwnedByAnotherUser() throws Exception {
        when(profileAppService.getProfile(any(UUID.class), any(String.class)))
                .thenThrow(new ProfileAccessDeniedException());

        mockMvc.perform(get("/api/v1/profiles/{id}", UUID.randomUUID())
                        .header("X-User-Id", "uid-other"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void getProfile_returns401WhenXUserIdIsMissing() throws Exception {
        when(profileAppService.getProfile(any(UUID.class), any()))
                .thenThrow(new IdentityRequiredException());

        mockMvc.perform(get("/api/v1/profiles/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
    }

    @Test
    void patchProfile_returns403WhenProfileIsOwnedByAnotherUser() throws Exception {
        when(profileAppService.updateProfileInfo(any()))
                .thenThrow(new ProfileAccessDeniedException());

        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .header("X-User-Id", "uid-other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Intruso"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void patchProfile_returns401WhenXUserIdIsMissing() throws Exception {
        when(profileAppService.updateProfileInfo(any()))
                .thenThrow(new IdentityRequiredException());

        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sin uid"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
    }

    @Test
    void patchSalaryExpectation_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-sal"));
        when(profileAppService.updateSalaryExpectation(any())).thenReturn(profile);

        mockMvc.perform(patch("/api/v1/profiles/{id}/salary-expectation", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-sal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 3500000}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void postSkills_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-skill"));
        when(profileAppService.addSkill(any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "level": "EXPERT", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void postSkills_returns409WhenDuplicate() throws Exception {
        when(profileAppService.addSkill(any())).thenThrow(new DuplicateSkillException("Java"));

        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "level": "ADVANCED", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Habilidad duplicada"));
    }

    @Test
    void postSkills_returns422WhenLevelIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("level"))
                .andExpect(jsonPath("$.errors[0].code").value("SKILL_LEVEL_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("Elige un nivel."));
    }

    @Test
    void patchProfile_shouldReturn422_whenBodyIsMalformed() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-patch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"));
    }

    @Test
    void deleteSkill_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delskill"));
        when(profileAppService.removeSkill(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/skills/{skillId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delskill"))
                .andExpect(status().isOk());
    }

    @Test
    void postReviewRequests_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-rev"));
        when(profileAppService.requestReview(any(), any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/review-requests", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-rev"))
                .andExpect(status().isCreated());
    }

    @Test
    void postReviewRequests_returns422WhenProfileIncomplete() throws Exception {
        when(profileAppService.requestReview(any(), any()))
                .thenThrow(new IncompleteProfileException(List.of("nombre", "resumen")));

        mockMvc.perform(post("/api/v1/profiles/{id}/review-requests", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-rev"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.missingRequirements").isArray());
    }

    @Test
    void postTargetRoles_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-role"));
        when(profileAppService.addTargetRole(any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    void postTargetRoles_returns409WhenDuplicate() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new DuplicateTargetRoleException("Backend Developer"));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Rol objetivo duplicado"));
    }

    @Test
    void postTargetRoles_returns422WhenMaxExceeded() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new MaxTargetRolesExceededException(5));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Máximo de roles objetivo alcanzado"));
    }

    @Test
    void deleteTargetRole_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delrole"));
        when(profileAppService.removeTargetRole(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/target-roles/{roleId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delrole"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH de rol objetivo sin professionalRoleId responde 422 con su código y no llega al servicio")
    void patchTargetRole_shouldReturn422_whenProfessionalRoleIdMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/{id}/target-roles/{roleId}", UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("professionalRoleId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFESSIONAL_ROLE_ID_REQUIRED"));
    }

    @Test
    void postCompletion_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-comp"));
        when(profileAppService.completeProfile(any(), any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/completion", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-comp"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Finalizar sin resumen ni habilidades responde la forma común con los dos requisitos")
    void postCompletion_shouldReturn422WithCommonShape_whenSummaryAndSkillsAreMissing() throws Exception {
        when(profileAppService.completeProfile(any(), any()))
                .thenThrow(new IncompleteProfileException(List.of("summary", "al menos 1 habilidad")));

        mockMvc.perform(post("/api/v1/profiles/{id}/completion", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-comp"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.detail").value("Todavía no cumples estos requisitos:"))
                .andExpect(jsonPath("$.missingRequirements.length()").value(2))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
