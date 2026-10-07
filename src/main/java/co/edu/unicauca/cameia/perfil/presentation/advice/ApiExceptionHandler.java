package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.BusinessException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.presentation.dto.CompletionErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.List;

import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.REQUEST_ID_HEADER;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.problem;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.requestId;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.respond;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.withHeaders;

/**
 * Única frontera donde las excepciones se traducen a HTTP (reglas de código, sección 7, regla 5).
 *
 * <p>Usa ProblemDetail de RFC 9457. Toda respuesta de error lleva {@code code} y {@code requestId},
 * y el encabezado {@code X-Request-Id}. Un fallo no controlado responde un mensaje genérico.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String IDENTITY_HEADER = "X-User-Id";

    /** Un campo rechazado, tal como lo necesita el cliente para marcarlo. */
    private record FieldProblem(String field, ErrorCode code, String message) { }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
        return reject(ex.getCode(), ex.getMessage(), request);
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

    /** Un valor que rechaza un objeto de valor, un enum o el formato de una fecha, sin campo asociado. */
    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    ResponseEntity<Object> handleInvalidValue(RuntimeException ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Valor rechazado sin campo: code={} requestId={} origen={}",
                ErrorCode.REQUEST_INVALID_VALUE, requestId, origin(ex));
        return respond(problem(ErrorCode.REQUEST_INVALID_VALUE, null), requestId);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.error("Fallo no controlado: requestId={}", requestId, ex);
        return respond(problem(ErrorCode.INTERNAL_ERROR, null), requestId);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Petición rechazada: code={} requestId={}", ErrorCode.VALIDATION_FAILED, requestId);
        var problem = problem(ErrorCode.VALIDATION_FAILED, null);
        problem.setProperty("errors", fieldProblems(ex));
        return respond(problem, requestId);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return reject(ErrorCode.REQUEST_BODY_INVALID_FORMAT, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing) {
            // Sin el encabezado de identidad no hay Usuario: 401. Cualquier otro encabezado ausente es un valor no válido.
            var code = IDENTITY_HEADER.equalsIgnoreCase(missing.getHeaderName())
                    ? ErrorCode.IDENTITY_REQUIRED : ErrorCode.REQUEST_INVALID_VALUE;
            return reject(code, null, request);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return reject(ErrorCode.ROUTE_NOT_FOUND, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return reject(ErrorCode.ROUTE_NOT_FOUND, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return withHeaders(reject(ErrorCode.METHOD_NOT_ALLOWED, null, request), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return withHeaders(reject(ErrorCode.CONTENT_TYPE_NOT_ALLOWED, null, request), headers);
    }

    /** Un identificador de la ruta que no es UUID tiene el código de su parámetro; nunca repite el valor. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "";
        return reject(ErrorCatalog.pathIdCode(name), null, request);
    }

    /**
     * Respuestas del framework sin código propio: conservan su estado y su texto, y ganan el
     * identificador de la petición y el charset, como el resto de los errores.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        var response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null || !(response.getBody() instanceof ProblemDetail problem)) {
            return response;
        }
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Petición rechazada por el framework: status={} requestId={}", statusCode.value(), requestId);
        return withHeaders(respond(problem, requestId), response.getHeaders());
    }

    /** Registra el rechazo y responde el problema del código, con el mensaje dado o el del catálogo. */
    private ResponseEntity<Object> reject(ErrorCode code, String detail, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Petición rechazada: code={} requestId={}", code, requestId);
        return respond(problem(code, detail), requestId);
    }

    private static List<FieldProblem> fieldProblems(MethodArgumentNotValidException ex) {
        var target = ex.getBindingResult().getTarget();
        var owner = target != null ? target.getClass().getSimpleName() : ex.getBindingResult().getObjectName();
        var firstPerField = new LinkedHashMap<String, FieldProblem>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            var code = ErrorCatalog.fieldCode(owner + "." + fe.getField() + "." + fe.getCode());
            var message = ErrorCatalog.fieldMessage(code);
            firstPerField.putIfAbsent(fe.getField(), new FieldProblem(fe.getField(), code, message));
        });
        return List.copyOf(firstPerField.values());
    }

    private static String origin(Exception ex) {
        var frame = ex.getStackTrace().length > 0 ? ex.getStackTrace()[0] : null;
        return frame == null ? "desconocido" : frame.getClassName() + "." + frame.getMethodName();
    }
}
