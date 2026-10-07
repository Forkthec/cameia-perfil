package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.BusinessException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.presentation.dto.CompletionErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Única frontera donde las excepciones se traducen a HTTP (reglas de código, sección 7, regla 5).
 *
 * <p>Usa ProblemDetail de RFC 9457. Toda respuesta de error lleva {@code code} y {@code requestId},
 * y el encabezado {@code X-Request-Id}. Un fallo no controlado responde un mensaje genérico.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String IDENTITY_HEADER = "X-User-Id";
    private static final MediaType PROBLEM_JSON_UTF8 = new MediaType("application", "problem+json", StandardCharsets.UTF_8);
    private static final Pattern REQUEST_ID_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    static final Map<ErrorCode, HttpStatus> STATUS = Map.ofEntries(
            Map.entry(ErrorCode.PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.PROFILE_NOT_ALLOWED, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.PROFILE_LIMIT_REACHED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.PROFILE_ALREADY_COMPLETED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.PROFILE_INCOMPLETE, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ErrorCode.PROFESSIONAL_ROLE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.TARGET_ROLE_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ErrorCode.TARGET_ROLE_ALREADY_EXISTS, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.TARGET_ROLE_NOT_ALLOWED, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ErrorCode.SKILL_ALREADY_EXISTS, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.IDENTITY_REQUIRED, HttpStatus.UNAUTHORIZED));

    private static final Map<ErrorCode, String> TITLES = Map.ofEntries(
            Map.entry(ErrorCode.PROFILE_NOT_FOUND, "Perfil no encontrado"),
            Map.entry(ErrorCode.PROFILE_NOT_ALLOWED, "Acceso denegado"),
            Map.entry(ErrorCode.PROFILE_LIMIT_REACHED, "Perfil ya existe"),
            Map.entry(ErrorCode.PROFILE_ALREADY_COMPLETED, "Perfil ya completado"),
            Map.entry(ErrorCode.PROFESSIONAL_ROLE_NOT_FOUND, "Rol profesional no encontrado"),
            Map.entry(ErrorCode.TARGET_ROLE_LIMIT_REACHED, "Máximo de roles objetivo alcanzado"),
            Map.entry(ErrorCode.TARGET_ROLE_ALREADY_EXISTS, "Rol objetivo duplicado"),
            Map.entry(ErrorCode.TARGET_ROLE_NOT_ALLOWED, "No se puede eliminar el último rol objetivo"),
            Map.entry(ErrorCode.SKILL_ALREADY_EXISTS, "Habilidad duplicada"),
            Map.entry(ErrorCode.IDENTITY_REQUIRED, "Identidad requerida"));
    private static final String SELECT_OPTION = "Selecciona una opción.";
    static final Map<String, ErrorCode> FIELD_CODES = Map.ofEntries(
            Map.entry("AddWorkExperienceRequest.company.NotBlank", ErrorCode.COMPANY_REQUIRED),
            Map.entry("AddWorkExperienceRequest.position.NotBlank", ErrorCode.POSITION_REQUIRED),
            Map.entry("AddWorkExperienceRequest.startDate.NotBlank", ErrorCode.START_DATE_REQUIRED),
            Map.entry("AddWorkExperienceRequest.employmentStatus.NotNull", ErrorCode.EMPLOYMENT_STATUS_REQUIRED),
            Map.entry("AddWorkExperienceRequest.provenance.NotNull", ErrorCode.PROVENANCE_REQUIRED),
            Map.entry("AddSkillRequest.provenance.NotBlank", ErrorCode.PROVENANCE_REQUIRED),
            Map.entry("AddTargetRoleRequest.provenance.NotNull", ErrorCode.PROVENANCE_REQUIRED),
            Map.entry("AddSkillRequest.skillName.NotBlank", ErrorCode.SKILL_NAME_REQUIRED),
            Map.entry("AddSkillRequest.level.NotBlank", ErrorCode.SKILL_LEVEL_REQUIRED),
            Map.entry("AddTargetRoleRequest.professionalRoleId.NotNull", ErrorCode.PROFESSIONAL_ROLE_ID_REQUIRED));

    private static final Map<ErrorCode, String> FIELD_MESSAGES = Map.ofEntries(
            Map.entry(ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa."),
            Map.entry(ErrorCode.POSITION_REQUIRED, "Ingresa el cargo."),
            Map.entry(ErrorCode.START_DATE_REQUIRED, "Ingresa la fecha de inicio."),
            Map.entry(ErrorCode.EMPLOYMENT_STATUS_REQUIRED, SELECT_OPTION),
            Map.entry(ErrorCode.PROVENANCE_REQUIRED, SELECT_OPTION),
            Map.entry(ErrorCode.SKILL_NAME_REQUIRED, "Ingresa una habilidad."),
            Map.entry(ErrorCode.SKILL_LEVEL_REQUIRED, "Elige un nivel."),
            Map.entry(ErrorCode.PROFESSIONAL_ROLE_ID_REQUIRED, SELECT_OPTION));

    /** Un campo rechazado, tal como lo necesita el cliente para marcarlo. */
    private record FieldProblem(String field, ErrorCode code, String message) { }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
        var status = STATUS.getOrDefault(ex.getCode(), HttpStatus.UNPROCESSABLE_ENTITY);
        return reject(status, ex.getCode(), TITLES.get(ex.getCode()), ex.getMessage(), request);
    }

    @ExceptionHandler(IncompleteProfileException.class)
    ResponseEntity<CompletionErrorResponse> handleIncompleteProfile(
            IncompleteProfileException ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Petición rechazada: code={} requestId={}", ex.getCode(), requestId);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .header(REQUEST_ID_HEADER, requestId)
                .body(new CompletionErrorResponse(ex.getMissingRequirements()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Valor rechazado sin campo: code={} requestId={} origen={}",
                ErrorCode.REQUEST_INVALID_VALUE, requestId, origin(ex));
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.REQUEST_INVALID_VALUE,
                "Valor no válido", "Revisa los datos enviados.", requestId, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.error("Fallo no controlado: requestId={}", requestId, ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Error interno", "Ocurrió un error. Inténtalo de nuevo.", requestId, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Petición rechazada: code={} requestId={}", ErrorCode.VALIDATION_FAILED, requestId);
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED,
                "Datos no válidos", "Revisa los campos marcados.", requestId, fieldProblems(ex));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return reject(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.REQUEST_BODY_INVALID_FORMAT,
                "Datos no válidos", "Revisa el formato de los datos enviados.", request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing && IDENTITY_HEADER.equalsIgnoreCase(missing.getHeaderName())) {
            return reject(HttpStatus.BAD_REQUEST, ErrorCode.IDENTITY_REQUIRED,
                    TITLES.get(ErrorCode.IDENTITY_REQUIRED), "Identidad del usuario requerida", request);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    private ResponseEntity<Object> reject(
            HttpStatus status, ErrorCode code, String title, String detail, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Petición rechazada: code={} requestId={}", code, requestId);
        return respond(status, code, title, detail, requestId, List.of());
    }

    private static ResponseEntity<Object> respond(HttpStatus status, ErrorCode code, String title,
            String detail, String requestId, List<FieldProblem> errors) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).contentType(PROBLEM_JSON_UTF8).header(REQUEST_ID_HEADER, requestId).body(problem);
    }

    private static List<FieldProblem> fieldProblems(MethodArgumentNotValidException ex) {
        var target = ex.getBindingResult().getTarget();
        var owner = target != null ? target.getClass().getSimpleName() : ex.getBindingResult().getObjectName();
        var firstPerField = new LinkedHashMap<String, FieldProblem>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            var code = FIELD_CODES.getOrDefault(owner + "." + fe.getField() + "." + fe.getCode(),
                    ErrorCode.VALIDATION_FAILED);
            var message = FIELD_MESSAGES.getOrDefault(code, "Revisa este campo.");
            firstPerField.putIfAbsent(fe.getField(), new FieldProblem(fe.getField(), code, message));
        });
        return List.copyOf(firstPerField.values());
    }

    private static String origin(Exception ex) {
        var frame = ex.getStackTrace().length > 0 ? ex.getStackTrace()[0] : null;
        return frame == null ? "desconocido" : frame.getClassName() + "." + frame.getMethodName();
    }

    /** Devuelve el identificador de la petición: el recibido si es válido, o uno nuevo. */
    private static String requestId(String received) {
        return received != null && REQUEST_ID_FORMAT.matcher(received).matches()
                ? received : UUID.randomUUID().toString();
    }
}
