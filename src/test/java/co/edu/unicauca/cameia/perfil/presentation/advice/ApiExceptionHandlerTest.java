package co.edu.unicauca.cameia.perfil.presentation.advice;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private static final String UUID_V4 =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final String DTO_PACKAGE = "co.edu.unicauca.cameia.perfil.presentation.dto";

    /** Lo que lanzará el controlador de prueba en la siguiente petición. */
    static Supplier<Exception> toThrow;

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() throws Exception {
            throw toThrow.get();
        }
    }

    MockMvc mockMvc;
    ListAppender<ILoggingEvent> logs;
    Logger handlerLogger;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
        handlerLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
    }

    static Stream<Object[]> businessExceptions() {
        var id = UUID.randomUUID();
        return Stream.of(
                new Object[]{new ProfileNotFoundException(id), 404, "PROFILE_NOT_FOUND"},
                new Object[]{new ProfileAccessDeniedException(), 403, "PROFILE_NOT_ALLOWED"},
                new Object[]{new ProfileAlreadyExistsException(), 409, "PROFILE_LIMIT_REACHED"},
                new Object[]{new ProfileAlreadyCompletedException(), 409, "PROFILE_ALREADY_COMPLETED"},
                new Object[]{new ProfessionalRoleNotFoundException(id), 404, "PROFESSIONAL_ROLE_NOT_FOUND"},
                new Object[]{new MaxTargetRolesExceededException(5), 422, "TARGET_ROLE_LIMIT_REACHED"},
                new Object[]{new DuplicateTargetRoleException("Backend"), 409, "TARGET_ROLE_ALREADY_EXISTS"},
                new Object[]{new LastTargetRoleException(), 422, "TARGET_ROLE_NOT_ALLOWED"},
                new Object[]{new DuplicateSkillException("Java"), 409, "SKILL_ALREADY_EXISTS"},
                new Object[]{new IdentityRequiredException(), 401, "IDENTITY_REQUIRED"});
    }

    @ParameterizedTest
    @MethodSource("businessExceptions")
    @DisplayName("Cada excepción de negocio responde su estado y su código")
    void businessException_shouldReturnItsStatusAndCode_whenThrown(
            Exception exception, int expectedStatus, String expectedCode) throws Exception {
        toThrow = () -> exception;

        mockMvc.perform(get("/boom"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("Toda respuesta de error declara application/problem+json con charset UTF-8")
    void errorResponse_shouldDeclareUtf8_whenProblemReturned() throws Exception {
        toThrow = ProfileAccessDeniedException::new;

        mockMvc.perform(get("/boom"))
                .andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"));
    }

    @Test
    @DisplayName("El perfil incompleto responde 422 con su cuerpo propio y el encabezado de petición")
    void incompleteProfile_shouldReturn422WithRequestIdHeader_whenThrown() throws Exception {
        toThrow = () -> new IncompleteProfileException(List.of("nombre"));

        mockMvc.perform(get("/boom"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.missingRequirements").isArray())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("Todo código tiene estado o se maneja en otro lugar, y solo los previstos")
    void everyErrorCode_shouldHaveStatusOrBeHandledElsewhere() {
        var handledElsewhere = EnumSet.of(ErrorCode.VALIDATION_FAILED,
                ErrorCode.REQUEST_BODY_INVALID_FORMAT, ErrorCode.REQUEST_INVALID_VALUE,
                ErrorCode.INTERNAL_ERROR, ErrorCode.COMPANY_REQUIRED, ErrorCode.POSITION_REQUIRED,
                ErrorCode.START_DATE_REQUIRED, ErrorCode.EMPLOYMENT_STATUS_REQUIRED,
                ErrorCode.PROVENANCE_REQUIRED, ErrorCode.SKILL_NAME_REQUIRED,
                ErrorCode.SKILL_LEVEL_REQUIRED, ErrorCode.PROFESSIONAL_ROLE_ID_REQUIRED);
        var withoutStatus = EnumSet.allOf(ErrorCode.class);
        withoutStatus.removeAll(ApiExceptionHandler.STATUS.keySet());

        assertThat(withoutStatus).isEqualTo(handledElsewhere);
    }

    @Test
    @DisplayName("Un identificador de petición válido se devuelve igual en cuerpo y encabezado")
    void requestId_shouldEchoValidHeader_whenPresent() throws Exception {
        toThrow = ProfileAccessDeniedException::new;

        mockMvc.perform(get("/boom").header("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.requestId").value("abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("Un identificador inválido se reemplaza por un UUID nuevo")
    void requestId_shouldBeGenerated_whenHeaderInvalid(String sent) throws Exception {
        toThrow = ProfileAccessDeniedException::new;

        var result = mockMvc.perform(get("/boom").header("X-Request-Id", sent)).andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).matches(UUID_V4).isNotEqualTo(sent);
    }

    @Test
    @DisplayName("Sin identificador de petición se genera un UUID nuevo")
    void requestId_shouldBeGenerated_whenHeaderMissing() throws Exception {
        toThrow = ProfileAccessDeniedException::new;

        var result = mockMvc.perform(get("/boom")).andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).matches(UUID_V4);
    }

    @Test
    @DisplayName("Un valor rechazado sin campo no filtra el mensaje y deja el origen en el log")
    void illegalArgument_shouldHideMessage_whenThrown() throws Exception {
        toThrow = () -> new IllegalArgumentException(
                "co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus.FREELANCE");

        var result = mockMvc.perform(get("/boom"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("co.edu");
        var requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("origen=").contains(requestId);
            assertThat(event.getFormattedMessage()).doesNotContain("FREELANCE");
        });
    }

    @Test
    @DisplayName("Un fallo no controlado responde un 500 genérico sin detalle")
    void unexpectedException_shouldReturnGeneric500_whenThrown() throws Exception {
        toThrow = () -> new RuntimeException("SELECT * FROM secreto");

        var result = mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("secreto");
    }

    @Test
    @DisplayName("Un valor rechazado sin traza registra el origen como desconocido")
    void illegalArgument_shouldLogUnknownOrigin_whenStackTraceIsEmpty() throws Exception {
        toThrow = () -> {
            var ex = new IllegalArgumentException("x");
            ex.setStackTrace(new StackTraceElement[0]);
            return ex;
        };

        mockMvc.perform(get("/boom")).andExpect(status().isUnprocessableEntity());

        assertThat(logs.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("origen=desconocido"));
    }

    @Test
    @DisplayName("Un error de enlace que no es el encabezado de identidad conserva la respuesta del framework")
    void bindingError_shouldKeepFrameworkResponse_whenNotTheIdentityHeader() throws Exception {
        toThrow = () -> new ServletRequestBindingException("enlace fallido");

        mockMvc.perform(get("/boom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("Un encabezado ausente que no es el de identidad conserva la respuesta del framework")
    void missingHeader_shouldKeepFrameworkResponse_whenNotTheIdentityHeader() throws Exception {
        var parameter = new MethodParameter(ApiExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        toThrow = () -> new MissingRequestHeaderException("X-Otro", parameter);

        mockMvc.perform(get("/boom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("La validación sin objeto destino usa el nombre del objeto y el código genérico")
    void validation_shouldFallBackToGenericCode_whenTargetIsNull() throws Exception {
        var bindingResult = mock(BindingResult.class);
        when(bindingResult.getTarget()).thenReturn(null);
        when(bindingResult.getObjectName()).thenReturn("unknown");
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("unknown", "extra", null, false, null, null, "msg de librería")));
        var ex = new MethodArgumentNotValidException(
                new MethodParameter(ApiExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1), bindingResult);

        var response = new ApiExceptionHandler().handleMethodArgumentNotValid(ex, new HttpHeaders(),
                HttpStatus.UNPROCESSABLE_ENTITY, new ServletWebRequest(new MockHttpServletRequest()));

        var body = (ProblemDetail) response.getBody();
        assertThat(body.getProperties()).containsEntry("code", ErrorCode.VALIDATION_FAILED);
        assertThat(body.getProperties().get("errors").toString())
                .contains("VALIDATION_FAILED").contains("Revisa este campo.").doesNotContain("librería");
    }

    @Test
    @DisplayName("Toda restricción de los DTO tiene su código de campo")
    void everyFieldConstraint_shouldHaveCode() throws Exception {
        var missing = new ArrayList<String>();
        for (Class<?> dto : dtoClasses()) {
            for (Field field : dto.getDeclaredFields()) {
                for (String constraint : constraintNames(field)) {
                    var key = dto.getSimpleName() + "." + field.getName() + "." + constraint;
                    if (!ApiExceptionHandler.FIELD_CODES.containsKey(key)) {
                        missing.add(key);
                    }
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    private static List<String> constraintNames(Field field) {
        var names = new ArrayList<String>();
        if (field.isAnnotationPresent(NotBlank.class)) {
            names.add("NotBlank");
        }
        if (field.isAnnotationPresent(NotNull.class)) {
            names.add("NotNull");
        }
        if (field.isAnnotationPresent(Size.class)) {
            names.add("Size");
        }
        return names;
    }

    private static List<Class<?>> dtoClasses() throws Exception {
        var directory = new File(Class.forName(DTO_PACKAGE + ".ProfileResponse")
                .getProtectionDomain().getCodeSource().getLocation().toURI())
                .toPath().resolve(DTO_PACKAGE.replace('.', '/')).toFile();
        var classes = new ArrayList<Class<?>>();
        for (String name : directory.list((dir, file) -> file.endsWith(".class") && !file.contains("$"))) {
            classes.add(Class.forName(DTO_PACKAGE + "." + name.substring(0, name.length() - 6)));
        }
        return classes;
    }
}
