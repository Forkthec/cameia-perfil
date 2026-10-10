package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.application.service.ProfileCreationAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileCreationInProgressException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock ProfileAppService profileAppService;
    @Mock ProfileCreationAppService profileCreationAppService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(profileAppService, profileCreationAppService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postProfiles_returns201WithProfileBody() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-001"));
        when(profileCreationAppService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ctrl-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.targetRoles").isArray());
    }

    @Test
    @DisplayName("Un Usuario que ya tiene su perfil recibe 403 PLAN_LIMIT con el mensaje del Plan Free y sin datos internos")
    void postProfiles_shouldReturn403PlanLimit_whenUserAlreadyHasProfile() throws Exception {
        when(profileCreationAppService.createProfile(any())).thenThrow(new ProfileLimitReachedException());

        var result = mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT"))
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
    @DisplayName("El perfil muestra de cada rol objetivo su id, su rol del catálogo, su nombre y su procedencia")
    void getProfile_shouldShowTargetRoleFields_whenProfileHasRoles() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ana-002"));
        profile.addTargetRole(new co.edu.unicauca.cameia.perfil.domain.model.TargetRole(
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Desarrollador Backend",
                co.edu.unicauca.cameia.perfil.domain.model.DataProvenance.MANUAL));
        when(profileAppService.getProfile(profile.getId().value(), "uid-ana-002")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/profiles/{id}", profile.getId().value()).header("X-User-Id", "uid-ana-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetRoles[0].id").value("44444444-4444-4444-8444-444444444444"))
                .andExpect(jsonPath("$.targetRoles[0].professionalRoleId").value("a0000001-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.targetRoles[0].roleTitle").value("Desarrollador Backend"))
                .andExpect(jsonPath("$.targetRoles[0].provenance").value("MANUAL"));
    }

    @Test
    @DisplayName("Crear un perfil ignora el cuerpo: la identidad sale solo del encabezado")
    void postProfiles_shouldIgnoreBody_whenBodySent() throws Exception {
        when(profileCreationAppService.createProfile(new CreateProfileCommand("uid-ana-001")))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ana-001")));

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"firebaseUid\":\"otro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(profileCreationAppService).createProfile(new CreateProfileCommand("uid-ana-001"));
    }

    @Test
    @DisplayName("Si la base de datos falla, crear un perfil responde el 500 genérico sin detalle")
    void postProfiles_shouldReturnGeneric500_whenDatabaseFails() throws Exception {
        when(profileCreationAppService.createProfile(any())).thenThrow(new DataAccessResourceFailureException("conexión"));

        var result = mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("conexión");
    }

    @Test
    @DisplayName("Si otra creación del mismo Usuario no termina a tiempo, crear un perfil responde 409 con su código")
    void postProfiles_shouldReturn409_whenCreationLockTimesOut() throws Exception {
        when(profileCreationAppService.createProfile(any())).thenThrow(new ProfileCreationInProgressException());

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ana-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_CREATION_IN_PROGRESS"))
                .andExpect(jsonPath("$.detail").value("Estamos creando tu perfil. Inténtalo de nuevo en unos segundos."))
                .andExpect(jsonPath("$.requestId").exists());
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
        when(profileCreationAppService.createProfile(new CreateProfileCommand(uid))).thenThrow(new IdentityRequiredException());

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", uid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres crea el perfil")
    void postProfiles_shouldReturn201_whenXUserIdHasMaxLength() throws Exception {
        var uid = "a".repeat(128);
        when(profileCreationAppService.createProfile(new CreateProfileCommand(uid)))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid(uid)));

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", uid))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres responde 401 sin repetir el valor")
    void postProfiles_shouldReturn401WithoutEcho_whenXUserIdIsTooLong() throws Exception {
        var uid = "a".repeat(129);
        when(profileCreationAppService.createProfile(new CreateProfileCommand(uid))).thenThrow(new IdentityRequiredException());

        var result = mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", uid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("aaaa");
    }

    private static final String EXPERIENCE_BASE = "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\"";

    private org.springframework.test.web.servlet.ResultActions postExperience(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/" + UUID.randomUUID() + "/work-experiences")
                .header("X-User-Id", "uid-ctrl-exp").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Comprueba 422, tipo de contenido con charset y que el cuerpo no filtra detalles internos. */
    private static org.springframework.test.web.servlet.ResultActions expectFieldError(
            org.springframework.test.web.servlet.ResultActions result, String field, String code, String message) throws Exception {
        var response = result.andExpect(status().isUnprocessableContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", containsString("application/problem+json")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", org.hamcrest.Matchers.containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.errors[0].message").value(message));
        assertThat(response.andReturn().getResponse().getContentAsString())
                .doesNotContain("\"trace\"").doesNotContain("\"exception\"").doesNotContain("co.edu");
        return response;
    }

    @ParameterizedTest(name = "{0}={1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            company  | "\\t\\n"
            company  | " "
            position | "\\t\\n"
            position | " "
            """)
    @DisplayName("Una empresa o un cargo con solo espacios, tabuladores o saltos responde que es obligatorio")
    void addWorkExperience_shouldReturnRequired_whenTextIsMissingOrBlank(String field, String value) throws Exception {
        var json = "company".equals(field)
                ? "{\"company\":" + value + ",\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\"}"
                : "{\"company\":\"ACME\",\"position\":" + value + ",\"employmentStatus\":\"CURRENT\"}";
        var message = "company".equals(field) ? "Ingresa la empresa." : "Ingresa el cargo.";

        expectFieldError(postExperience(json), field, field.toUpperCase() + "_REQUIRED", message);
    }

    @ParameterizedTest(name = "{0} de {1} caracteres")
    @MethodSource("tooLongTexts")
    @DisplayName("Un texto que supera su límite en puntos de código responde 422 con su código y su mensaje")
    void addWorkExperience_shouldReturnTooLong_whenTextExceedsLimit(String field, String value, int length, String code,
            String message) throws Exception {
        var company = "company".equals(field) ? value : "ACME";
        var position = "position".equals(field) ? value : "Dev";
        var description = "description".equals(field) ? value : "ok";
        var json = "{\"company\":\"" + company + "\",\"position\":\"" + position + "\",\"description\":\"" + description
                + "\",\"employmentStatus\":\"CURRENT\"}";

        expectFieldError(postExperience(json), field, code, message);
    }

    static Stream<Arguments> tooLongTexts() {
        var company = "La empresa no puede superar los 100 caracteres.";
        var position = "El cargo no puede superar los 100 caracteres.";
        var description = "La descripción no puede superar los 500 caracteres.";
        return Stream.of(
                Arguments.of("company", "a".repeat(101), 101, "COMPANY_TOO_LONG", company),
                Arguments.of("company", "a".repeat(100) + "😀", 101, "COMPANY_TOO_LONG", company),
                Arguments.of("position", "a".repeat(101), 101, "POSITION_TOO_LONG", position),
                Arguments.of("description", "a".repeat(501), 501, "DESCRIPTION_TOO_LONG", description),
                Arguments.of("description", "a".repeat(499) + "😀😀", 501, "DESCRIPTION_TOO_LONG", description));
    }

    @ParameterizedTest(name = "{0}={1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            startDate | 2020-13
            startDate | 2020-00
            startDate | 20-01
            startDate | 2020/01
            startDate | abc
            startDate | 2020-1
            endDate   | 2020-13
            endDate   | 2020-01-15
            endDate   | 2020
            """)
    @DisplayName("Una fecha mal escrita responde 422 en su campo con el formato esperado")
    void addWorkExperience_shouldReturnDateInvalidFormat_whenDateIsMalformed(String field, String value) throws Exception {
        var code = "startDate".equals(field) ? "START_DATE_INVALID_FORMAT" : "END_DATE_INVALID_FORMAT";
        var json = EXPERIENCE_BASE + ",\"" + field + "\":\"" + value + "\"}";

        expectFieldError(postExperience(json), field, code, "Ingresa una fecha válida con el formato mm/aaaa.");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"FREELANCE", "current", "Current"})
    @DisplayName("Un estado que no es una opción responde 422 con su código")
    void addWorkExperience_shouldReturnStatusInvalid_whenStatusIsUnknown(String status) throws Exception {
        var json = "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"" + status + "\"}";

        expectFieldError(postExperience(json), "employmentStatus", "EMPLOYMENT_STATUS_INVALID_VALUE", "Selecciona una opción.");
    }

    @Test
    @DisplayName("Un estado vacío responde que es obligatorio")
    void addWorkExperience_shouldReturnStatusRequired_whenStatusIsEmpty() throws Exception {
        var json = "{\"company\":\"ACME\",\"position\":\"Dev\",\"employmentStatus\":\"\"}";

        expectFieldError(postExperience(json), "employmentStatus", "EMPLOYMENT_STATUS_REQUIRED", "Selecciona una opción.");
    }

    @Test
    @DisplayName("Una procedencia en el cuerpo se ignora: la experiencia se guarda MANUAL")
    void addWorkExperience_shouldSaveManual_whenBodyDeclaresAiProvenance() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-exp"));
        profile.addWorkExperience(co.edu.unicauca.cameia.perfil.domain.model.WorkExperience.create(
                new co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft("ACME", "Dev", null, null, null,
                        co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus.CURRENT),
                new co.edu.unicauca.cameia.perfil.domain.model.DateBounds(java.time.YearMonth.of(2026, 10), null)));
        when(profileAppService.addWorkExperience(any())).thenReturn(profile);

        postExperience(EXPERIENCE_BASE + ",\"provenance\":\"AI_SUGGESTED\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workExperiences[0].provenance").value("MANUAL"));

        var command = ArgumentCaptor.forClass(co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand.class);
        verify(profileAppService).addWorkExperience(command.capture());
        assertThat(command.getValue().company()).isEqualTo("ACME");
    }

    @Test
    @DisplayName("Varios campos inválidos se informan a la vez, uno por campo")
    void addWorkExperience_shouldReturnEveryInvalidField_whenSeveralFail() throws Exception {
        var json = "{\"company\":\"\",\"position\":\"" + "a".repeat(101) + "\",\"startDate\":\"2024-13\",\"employmentStatus\":\"CURRENT\"}";

        postExperience(json).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "company", "position", "startDate")));
    }

    @Test
    @DisplayName("Una empresa con un carácter nulo se rechaza con su código")
    void addWorkExperience_shouldRejectControlCharacter_whenCompanyHasNul() throws Exception {
        var json = "{\"company\":\"Acme\\u0000\",\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\"}";

        expectFieldError(postExperience(json), "company", "COMPANY_INVALID_CHARACTERS", "La empresa tiene caracteres no permitidos.");
    }

    @Test
    @DisplayName("Los saltos de línea internos de la descripción se conservan y los de los extremos se recortan")
    void addWorkExperience_shouldKeepInnerLineBreaks_whenDescriptionHasThem() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-exp"));
        when(profileAppService.addWorkExperience(any())).thenReturn(profile);

        postExperience(EXPERIENCE_BASE + ",\"description\":\"\\nLínea 1\\nLínea 2\\n\"}").andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand.class);
        verify(profileAppService).addWorkExperience(command.capture());
        assertThat(command.getValue().description()).isEqualTo("Línea 1\nLínea 2");
    }

    @Test
    @DisplayName("Un número en lugar del texto de la empresa llega al servicio como texto")
    void addWorkExperience_shouldTreatNumberAsText_whenCompanyIsNumber() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-exp"));
        when(profileAppService.addWorkExperience(any())).thenReturn(profile);

        postExperience("{\"company\":123,\"position\":\"Dev\",\"employmentStatus\":\"CURRENT\"}").andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand.class);
        verify(profileAppService).addWorkExperience(command.capture());
        assertThat(command.getValue().company()).isEqualTo("123");
    }

    @Test
    @DisplayName("Agregar la quinta experiencia responde 409 con el máximo")
    void addWorkExperience_shouldReturn409_whenLimitReached() throws Exception {
        when(profileAppService.addWorkExperience(any()))
                .thenThrow(new co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceLimitReachedException());

        postExperience(EXPERIENCE_BASE + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORK_EXPERIENCE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.detail").value("Ya tienes el máximo de 4 experiencias laborales."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("Agregar una experiencia sin fecha de nacimiento replicada responde 503 con el texto del criterio")
    void addWorkExperience_shouldReturn503_whenBirthDateIsUnavailable() throws Exception {
        when(profileAppService.addWorkExperience(any()))
                .thenThrow(new co.edu.unicauca.cameia.perfil.domain.exception.BirthDateUnavailableException());

        postExperience(EXPERIENCE_BASE + "}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("BIRTH_DATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("Actualizar un rol objetivo sin professionalRoleId responde 422 con su campo, no 500")
    void updateTargetRole_shouldReturnRoleIdRequired_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/{id}/target-roles/{roleId}", UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("professionalRoleId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFESSIONAL_ROLE_ID_REQUIRED"));
    }

    @Test
    @DisplayName("Actualizar un rol objetivo que no está en el perfil responde 404 con su código")
    void updateTargetRole_shouldReturn404_whenRoleIsNotInProfile() throws Exception {
        when(profileAppService.updateTargetRole(any())).thenThrow(new TargetRoleNotFoundException());

        mockMvc.perform(patch("/api/v1/profiles/{id}/target-roles/{roleId}", UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionalRoleId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_ROLE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No encontramos lo que buscabas."));
    }

    @Test
    @DisplayName("Una formación sin inProgress se agrega como no en curso, no como cuerpo ilegible")
    void addEducation_shouldTreatMissingInProgressAsFalse_whenFieldIsAbsent() throws Exception {
        when(profileAppService.addEducation(any()))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ctrl-edu")));
        var body = """
                {"institution":"Unicauca","degree":"Sistemas","level":"UNDERGRADUATE","startDate":"2018-01"}
                """;

        mockMvc.perform(post("/api/v1/profiles/{id}/educations", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-edu")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(AddEducationCommand.class);
        verify(profileAppService).addEducation(command.capture());
        assertThat(command.getValue().inProgress()).isFalse();
    }

    private static final String EDUCATION_BASE =
            "{\"institution\":\"Unicauca\",\"degree\":\"Sistemas\",\"level\":\"UNDERGRADUATE\",\"startDate\":\"2018-01\"";

    private org.springframework.test.web.servlet.ResultActions postEducation(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/" + UUID.randomUUID() + "/educations")
                .header("X-User-Id", "uid-ctrl-edu").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("Una formación con el cuerpo vacío informa los cuatro campos obligatorios a la vez")
    void addEducation_shouldReturnAllRequiredFields_whenAllAreMissing() throws Exception {
        postEducation("{}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field=='institution')].message").value("Ingresa la institución."))
                .andExpect(jsonPath("$.errors[?(@.field=='degree')].message").value("Ingresa el título obtenido."))
                .andExpect(jsonPath("$.errors[?(@.field=='level')].message").value("Elige un nivel educativo."))
                .andExpect(jsonPath("$.errors[?(@.field=='startDate')].message").value("Ingresa la fecha de inicio."));
    }

    @ParameterizedTest(name = "{0}={1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            institution | "\\t\\n"
            institution | " "
            institution | ""
            institution | null
            degree      | "\\t\\n"
            degree      | " "
            degree      | ""
            degree      | null
            """)
    @DisplayName("Una institución o un título ausente, nulo, vacío o en blanco responde que es obligatorio")
    void addEducation_shouldReturnRequired_whenTextIsMissingOrBlank(String field, String value) throws Exception {
        var json = "institution".equals(field)
                ? "{\"institution\":" + value + ",\"degree\":\"Ing\",\"level\":\"TECHNICAL\",\"startDate\":\"2018-01\"}"
                : "{\"institution\":\"Unicauca\",\"degree\":" + value + ",\"level\":\"TECHNICAL\",\"startDate\":\"2018-01\"}";
        var message = "institution".equals(field) ? "Ingresa la institución." : "Ingresa el título obtenido.";

        expectFieldError(postEducation(json), field, field.equals("institution") ? "INSTITUTION_REQUIRED" : "DEGREE_REQUIRED", message);
    }

    @ParameterizedTest(name = "{0} de {1} puntos de código")
    @org.junit.jupiter.params.provider.CsvSource({"institution,151", "degree,151"})
    @DisplayName("Un texto de 151 puntos de código responde 422 con su código y su mensaje")
    void addEducation_shouldReturnTooLong_whenTextExceedsLimit(String field, int length) throws Exception {
        var institution = "institution".equals(field) ? "a".repeat(length) : "Unicauca";
        var degree = "degree".equals(field) ? "a".repeat(length) : "Sistemas";
        var json = "{\"institution\":\"" + institution + "\",\"degree\":\"" + degree
                + "\",\"level\":\"TECHNICAL\",\"startDate\":\"2018-01\"}";
        var code = "institution".equals(field) ? "INSTITUTION_TOO_LONG" : "DEGREE_TOO_LONG";
        var message = "institution".equals(field) ? "La institución no puede superar los 150 caracteres."
                : "El título obtenido no puede superar los 150 caracteres.";

        expectFieldError(postEducation(json), field, code, message);
    }

    @Test
    @DisplayName("Un emoji que lleva el texto a 151 puntos de código se rechaza y 150 se acepta")
    void addEducation_shouldCountCodePoints_whenTextEndsWithEmoji() throws Exception {
        when(profileAppService.addEducation(any())).thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ctrl-edu")));
        var atLimit = "{\"institution\":\"" + "a".repeat(149) + "😀\",\"degree\":\"" + "b".repeat(150)
                + "\",\"level\":\"TECHNICAL\",\"startDate\":\"2018-01\"}";
        var overLimit = "{\"institution\":\"" + "a".repeat(149) + "😀😀\",\"degree\":\"Sistemas\",\"level\":\"TECHNICAL\",\"startDate\":\"2018-01\"}";

        postEducation(atLimit).andExpect(status().isCreated());
        expectFieldError(postEducation(overLimit), "institution", "INSTITUTION_TOO_LONG",
                "La institución no puede superar los 150 caracteres.");
    }

    @ParameterizedTest(name = "nivel {0}")
    @ValueSource(strings = {"DOCTORATE", "undergraduate"})
    @DisplayName("Un nivel que no es una opción responde 422 con su código")
    void addEducation_shouldReturnLevelInvalid_whenLevelIsUnknown(String level) throws Exception {
        var json = "{\"institution\":\"Unicauca\",\"degree\":\"Sistemas\",\"level\":\"" + level + "\",\"startDate\":\"2018-01\"}";

        expectFieldError(postEducation(json), "level", "EDUCATION_LEVEL_INVALID_VALUE", SELECT_OPTION);
    }

    @ParameterizedTest(name = "{0}={1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            startDate | 2024
            startDate | 2020-13
            startDate | 20-01
            startDate | 2020/01
            endDate   | 2024
            endDate   | 2020-13
            endDate   | 2020-01-15
            """)
    @DisplayName("Una fecha mal escrita, incluido un año suelto, responde 422 en su campo con el formato esperado")
    void addEducation_shouldReturnDateInvalidFormat_whenDateIsMalformed(String field, String value) throws Exception {
        var code = "startDate".equals(field) ? "START_DATE_INVALID_FORMAT" : "END_DATE_INVALID_FORMAT";
        var base = "startDate".equals(field)
                ? "{\"institution\":\"Unicauca\",\"degree\":\"Sistemas\",\"level\":\"TECHNICAL\",\"endDate\":\"2021-01\",\"startDate\":\"" + value + "\"}"
                : EDUCATION_BASE + ",\"endDate\":\"" + value + "\"}";

        expectFieldError(postEducation(base), field, code, "Ingresa una fecha válida con el formato mm/aaaa.");
    }

    @Test
    @DisplayName("Un inProgress que es texto no booleano responde 422 de cuerpo ilegible")
    void addEducation_shouldRejectInProgressThatIsNotBoolean_whenValueIsText() throws Exception {
        postEducation(EDUCATION_BASE + ",\"inProgress\":\"abc\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"));
    }

    /**
     * Fija lo que Jackson hace hoy: convierte {@code 1}, {@code 0} y {@code "true"} a booleano, igual que convierte
     * {@code 123} a texto en la empresa. Si se quisiera rechazarlos habría que desactivar la conversión de escalares
     * para todos los cuerpos, no solo para este campo.
     */
    @ParameterizedTest(name = "inProgress={0} llega como {1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            1        | true
            0        | false
            "true"   | true
            "false"  | false
            """)
    @DisplayName("Un inProgress numérico o booleano entre comillas se convierte a booleano, como en el resto de los cuerpos")
    void addEducation_shouldRecordJacksonBehaviour_whenInProgressIsNumberOrQuotedBoolean(String value, boolean expected)
            throws Exception {
        when(profileAppService.addEducation(any()))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ctrl-edu")));

        postEducation(EDUCATION_BASE + ",\"inProgress\":" + value + "}").andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(AddEducationCommand.class);
        verify(profileAppService).addEducation(command.capture());
        assertThat(command.getValue().inProgress()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Una procedencia y un área de estudio en el cuerpo se ignoran")
    void addEducation_shouldIgnoreProvenanceAndFieldOfStudy_whenSent() throws Exception {
        when(profileAppService.addEducation(any())).thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ctrl-edu")));

        postEducation(EDUCATION_BASE + ",\"provenance\":\"AI_SUGGESTED\",\"fieldOfStudy\":\"Sistemas\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.educations").isEmpty());
        verify(profileAppService).addEducation(any());
    }

    @Test
    @DisplayName("Agregar la sexta formación responde 409 con el máximo y sin errors")
    void addEducation_shouldReturn409_whenLimitReached() throws Exception {
        when(profileAppService.addEducation(any()))
                .thenThrow(new co.edu.unicauca.cameia.perfil.domain.exception.EducationLimitReachedException());

        postEducation(EDUCATION_BASE + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDUCATION_LIMIT_REACHED"))
                .andExpect(jsonPath("$.detail").value("Ya tienes el máximo de 5 formaciones académicas."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("Eliminar la última formación de un perfil activo responde 409 con su código")
    void removeEducation_shouldReturn409_whenLastEducationOfActiveProfile() throws Exception {
        when(profileAppService.removeEducation(any(), any(), any()))
                .thenThrow(new co.edu.unicauca.cameia.perfil.domain.exception.EducationNotAllowedException());

        mockMvc.perform(delete("/api/v1/profiles/{id}/educations/{eduId}", UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-edu"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDUCATION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value(
                        "No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("Eliminar una formación que no es del perfil responde 404 con su código")
    void removeEducation_shouldReturn404_whenEducationIsNotInProfile() throws Exception {
        when(profileAppService.removeEducation(any(), any(), any()))
                .thenThrow(new co.edu.unicauca.cameia.perfil.domain.exception.EducationNotFoundException());

        mockMvc.perform(delete("/api/v1/profiles/{id}/educations/{eduId}", UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-edu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EDUCATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("Actualizar la expectativa salarial sin monto responde 422 con su campo, no 500")
    void updateSalaryExpectation_shouldReturnAmountRequired_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/{id}/salary-expectation", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-salary")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].code").value("SALARY_EXPECTATION_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("Ingresa la expectativa salarial."));
    }

    static Stream<Arguments> frameworkErrors() {
        var profileId = UUID.randomUUID();
        return Stream.of(
                Arguments.of(get("/api/v1/no-existe"), 404, "ROUTE_NOT_FOUND", "No existe la ruta solicitada."),
                Arguments.of(delete("/api/v1/profiles"), 405, "METHOD_NOT_ALLOWED",
                        "Método no permitido."),
                Arguments.of(patch("/api/v1/profiles/" + profileId).header("X-User-Id", "uid-ctrl-415")
                                .contentType(MediaType.TEXT_PLAIN).content("x"),
                        415, "MEDIA_TYPE_NOT_ALLOWED", "Tipo de contenido no admitido."),
                Arguments.of(get("/api/v1/profiles/no-es-uuid").header("X-User-Id", "uid-ctrl-id"),
                        422, "PROFILE_ID_INVALID_FORMAT", "El identificador del perfil no es válido."),
                Arguments.of(delete("/api/v1/profiles/" + profileId + "/skills/xyz").header("X-User-Id", "uid-ctrl-id"),
                        422, "SKILL_ID_INVALID_FORMAT", "El identificador de la habilidad no es válido."),
                Arguments.of(delete("/api/v1/profiles/" + profileId + "/work-experiences/xyz").header("X-User-Id", "uid-ctrl-id"),
                        422, "WORK_EXPERIENCE_ID_INVALID_FORMAT", "El identificador de la experiencia no es válido."),
                Arguments.of(delete("/api/v1/profiles/" + profileId + "/educations/xyz").header("X-User-Id", "uid-ctrl-id"),
                        422, "EDUCATION_ID_INVALID_FORMAT", "El identificador de la formación no es válido."),
                Arguments.of(delete("/api/v1/profiles/" + profileId + "/target-roles/xyz").header("X-User-Id", "uid-ctrl-id"),
                        422, "TARGET_ROLE_ID_INVALID_FORMAT", "El identificador del rol objetivo no es válido."));
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
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.detail").value("Tipo de respuesta no admitido."));
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
    private static final String EDUCATIONS = "/educations";
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
            }
            case SKILLS -> {
                body.put("skillName", "\"Java\"");
                body.put("level", "\"ADVANCED\"");
                body.put("provenance", "\"MANUAL\"");
            }
            case EDUCATIONS -> {
                body.put("institution", "\"Unicauca\"");
                body.put("degree", "\"Sistemas\"");
                body.put("level", "\"UNDERGRADUATE\"");
                body.put("startDate", "\"2018-01\"");
                body.put("inProgress", "false");
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
                new Object[]{WORK_EXPERIENCE, "employmentStatus", false, "EMPLOYMENT_STATUS_REQUIRED", SELECT_OPTION},
                new Object[]{SKILLS, "skillName", true, "SKILL_NAME_REQUIRED", "Ingresa una habilidad."},
                new Object[]{SKILLS, "level", true, "SKILL_LEVEL_REQUIRED", "Elige un nivel."},
                new Object[]{SKILLS, "provenance", true, "PROVENANCE_REQUIRED", SELECT_OPTION},
                new Object[]{TARGET_ROLES, "professionalRoleId", false, "PROFESSIONAL_ROLE_ID_REQUIRED", SELECT_OPTION},
                new Object[]{EDUCATIONS, "institution", true, "INSTITUTION_REQUIRED", "Ingresa la institución."},
                new Object[]{EDUCATIONS, "degree", true, "DEGREE_REQUIRED", "Ingresa el título obtenido."},
                new Object[]{EDUCATIONS, "level", true, "EDUCATION_LEVEL_REQUIRED", "Elige un nivel educativo."},
                new Object[]{EDUCATIONS, "startDate", true, "START_DATE_REQUIRED", "Ingresa la fecha de inicio."});
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
                .andExpect(status().isUnprocessableContent())
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
        when(profileAppService.addSkill(any())).thenThrow(new DuplicateSkillException());

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
    @DisplayName("Una habilidad sin nivel responde 422 con el código y el mensaje del nivel")
    void addSkill_shouldReturnSkillLevelRequired_whenLevelIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isUnprocessableContent())
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Eliminar una experiencia que no es del perfil responde 404 con su código y el texto de CA-2.4.59")
    void removeWorkExperience_shouldReturn404_whenExperienceIsNotInProfile() throws Exception {
        when(profileAppService.removeWorkExperience(any(), any(), any()))
                .thenThrow(new WorkExperienceNotFoundException());

        mockMvc.perform(delete("/api/v1/profiles/{id}/work-experiences/{expId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delexp"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_EXPERIENCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No encontramos lo que buscabas."));
    }

    @Test
    @DisplayName("Eliminar una habilidad responde 204 sin cuerpo")
    void removeSkill_shouldReturn204WithoutBody_whenRemoved() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delskill"));
        when(profileAppService.removeSkill(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/skills/{skillId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delskill"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Eliminar una experiencia laboral responde 204 sin cuerpo")
    void removeWorkExperience_shouldReturn204WithoutBody_whenRemoved() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delexp"));
        when(profileAppService.removeWorkExperience(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/work-experiences/{expId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delexp"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Eliminar una formación responde 204 sin cuerpo")
    void removeEducation_shouldReturn204WithoutBody_whenRemoved() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-deledu"));
        when(profileAppService.removeEducation(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/educations/{eduId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-deledu"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
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
                .andExpect(status().isUnprocessableContent())
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

    @ParameterizedTest(name = "provenance = {0}")
    @ValueSource(strings = {"\"AI_SUGGESTED\"", "\"X\"", "null", "ausente"})
    @DisplayName("El cuerpo solo aporta el rol: la procedencia, la identidad y el id que envía el cliente se ignoran")
    void postTargetRoles_shouldIgnoreProvenance_whenClientSendsIt(String provenance) throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-role"));
        when(profileAppService.addTargetRole(any())).thenReturn(profile);
        var roleId = UUID.randomUUID();
        var command = ArgumentCaptor.forClass(co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand.class);
        var provenanceField = "ausente".equals(provenance) ? "" : ", \"provenance\": " + provenance;

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionalRoleId\": \"%s\"%s, \"firebaseUid\": \"otro\", \"id\": \"x\"}"
                                .formatted(roleId, provenanceField)))
                .andExpect(status().isCreated());

        verify(profileAppService).addTargetRole(command.capture());
        assertThat(command.getValue().professionalRoleId()).isEqualTo(roleId);
        assertThat(command.getValue().uid()).isEqualTo("uid-ctrl-role");
    }

    @Test
    @DisplayName("Un rol inactivo responde 422 con el campo professionalRoleId y su código")
    void postTargetRoles_shouldReturn422WithField_whenRoleIsInactive() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(InvalidFieldsException.of(
                "professionalRoleId", co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.PROFESSIONAL_ROLE_DISABLED,
                "No encontramos ese rol en el catálogo."));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionalRoleId\": \"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors[0].field").value("professionalRoleId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFESSIONAL_ROLE_DISABLED"))
                .andExpect(jsonPath("$.errors[0].message").value("No encontramos ese rol en el catálogo."));
    }

    @Test
    @DisplayName("Un cuerpo sin professionalRoleId responde 422 con PROFESSIONAL_ROLE_ID_REQUIRED y sin errores de procedencia")
    void postTargetRoles_shouldReturnRoleIdRequired_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("professionalRoleId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFESSIONAL_ROLE_ID_REQUIRED"));
    }

    @ParameterizedTest(name = "professionalRoleId = {0}")
    @ValueSource(strings = {"\"no-es-uuid\"", "123", "{}"})
    @DisplayName("Un professionalRoleId que no es UUID responde 422 de formato sin repetir el valor")
    void postTargetRoles_shouldReturnBodyInvalid_whenRoleIdIsNotUuid(String roleId) throws Exception {
        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionalRoleId\": " + roleId + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.detail").value("Revisa el formato de los datos enviados."))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("no-es-uuid"))));
    }

    @Test
    @DisplayName("Un professionalRoleId vacío responde 422 de campo obligatorio, igual que si faltara")
    void postTargetRoles_shouldReturnRoleIdRequired_whenRoleIdIsEmptyString() throws Exception {
        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionalRoleId\": \"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("professionalRoleId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFESSIONAL_ROLE_ID_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("Selecciona una opción."));
    }

    @Test
    void postTargetRoles_returns409WhenDuplicate() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new DuplicateTargetRoleException());

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
    @DisplayName("El sexto rol objetivo responde 409 con el código y el mensaje del máximo, sin errors")
    void postTargetRoles_shouldReturn409_whenMaxReached() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new MaxTargetRolesExceededException(5));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_ROLE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.detail").value("Ya tienes el máximo de 5 roles objetivo."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("Eliminar un rol objetivo responde 204 sin cuerpo")
    void removeTargetRole_shouldReturn204WithoutBody_whenRemoved() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delrole"));
        when(profileAppService.removeTargetRole(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/target-roles/{roleId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delrole"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Eliminar un rol objetivo ignora el cuerpo de la petición y responde 204")
    void removeTargetRole_shouldIgnoreBody_whenBodyIsSent() throws Exception {
        var profileId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        when(profileAppService.removeTargetRole(profileId, "uid-ctrl-delrole", roleId))
                .thenReturn(ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delrole")));

        mockMvc.perform(delete("/api/v1/profiles/{id}/target-roles/{roleId}", profileId, roleId)
                        .header("X-User-Id", "uid-ctrl-delrole")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(profileAppService).removeTargetRole(profileId, "uid-ctrl-delrole", roleId);
    }

    @Test
    @DisplayName("Eliminar el último rol de un perfil activo responde 409 con su código y su mensaje, sin errors")
    void removeTargetRole_shouldReturn409_whenLastRoleOfActiveProfile() throws Exception {
        when(profileAppService.removeTargetRole(any(), any(), any()))
                .thenThrow(new co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException());

        mockMvc.perform(delete("/api/v1/profiles/{id}/target-roles/{roleId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delrole"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.code").value("TARGET_ROLE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value("No puedes quedarte sin roles objetivo con el perfil activo."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("PATCH de rol objetivo sin professionalRoleId responde 422 con su código y no llega al servicio")
    void patchTargetRole_shouldReturn422_whenProfessionalRoleIdMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/{id}/target-roles/{roleId}", UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("professionalRoleId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFESSIONAL_ROLE_ID_REQUIRED"));
    }

    @Test
    @DisplayName("Finalizar un perfil completo responde 200 con el perfil")
    void completeProfile_shouldReturn200_whenRequirementsAreMet() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-comp"));
        when(profileAppService.completeProfile(any(), any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/completion", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-comp"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Finalizar sin resumen ni habilidades responde la forma común con los dos requisitos")
    void postCompletion_shouldReturn422WithCommonShape_whenSummaryAndSkillsAreMissing() throws Exception {
        when(profileAppService.completeProfile(any(), any()))
                .thenThrow(new IncompleteProfileException(List.of("SUMMARY", "SKILLS")));

        mockMvc.perform(post("/api/v1/profiles/{id}/completion", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-comp"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.detail").value("Todavía no cumples estos requisitos:"))
                .andExpect(jsonPath("$.missingRequirements.length()").value(2))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
