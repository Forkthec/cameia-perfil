package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.BusinessException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import jakarta.validation.ConstraintViolation;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
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

import java.util.LinkedHashMap;
import java.util.List;

import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.REQUEST_ID_HEADER;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.firebaseUid;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.problem;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.requestId;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.respond;
import static co.edu.unicauca.cameia.perfil.presentation.advice.ProblemResponses.withHeaders;

/**
 * Única frontera donde las excepciones se traducen a HTTP: decide el código de cada excepción.
 *
 * <p>Usa ProblemDetail de RFC 9457. Toda respuesta de error lleva {@code code} y {@code requestId},
 * y el encabezado {@code X-Request-Id}. Un fallo no controlado responde un mensaje genérico.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Un campo rechazado, tal como lo necesita el cliente para marcarlo. */
    private record FieldProblem(String field, ErrorCode code, String message) { }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
        var problem = problem(ex.getCode(), ex.getMessage());
        if (ex instanceof IncompleteProfileException incomplete) {
            // La finalización incompleta lista, además, los requisitos que faltan.
            problem.setProperty("missingRequirements", incomplete.getMissingRequirements());
        }
        if (ex instanceof InvalidFieldsException invalid) {
            // El dominio rechazó campos: responde igual que Bean Validation, un elemento por campo.
            problem.setProperty("errors", invalid.getErrors().stream()
                    .map(e -> new FieldProblem(e.field(), e.code(), e.message())).toList());
        }
        return reject(problem, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        // Sin los mensajes de la cadena: pueden traer datos del perfil (valores de columnas, SQL con parámetros).
        log.error("Fallo no controlado: requestId={} firebaseUid={} sqlState={}",
                requestId, firebaseUid(request), RedactedException.sqlState(ex), RedactedException.of(ex));
        return respond(problem(ErrorCode.INTERNAL_ERROR, null), requestId);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problem = problem(ErrorCode.VALIDATION_FAILED, null);
        problem.setProperty("errors", fieldProblems(ex));
        return reject(problem, request);
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
            var code = ProblemResponses.IDENTITY_HEADER.equalsIgnoreCase(missing.getHeaderName())
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
        return withHeaders(reject(ErrorCode.MEDIA_TYPE_NOT_ALLOWED, null, request), headers);
    }

    /** Un identificador de la ruta que no es UUID tiene el código de su parámetro; nunca repite el valor. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "";
        return reject(ErrorCatalog.pathIdCode(name), null, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return reject(ErrorCode.MEDIA_TYPE_NOT_ACCEPTABLE, null, request);
    }

    /**
     * Respaldo para las demás excepciones del framework, sin el texto de Spring: un 400 es un dato del cliente
     * (422 de valor no válido); un 5xx, el 500 genérico. Otro estado no tiene código en el catálogo: responde
     * el 500 genérico y se registra en {@code ERROR} para darle su código, en vez de disfrazarlo de error del cliente.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        var response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null; // La respuesta ya se envió: no queda nada que responder.
        }
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return withHeaders(invalidValue(ex, request), response.getHeaders());
        }
        if (!statusCode.is5xxServerError()) {
            log.error("Rechazo del framework sin código en el catálogo: estado={} origen={}",
                    statusCode.value(), RedactedException.origin(ex));
        }
        return withHeaders(handleUnexpected(ex, request), response.getHeaders());
    }

    /** Un 400 del framework sin código propio: responde el valor no válido y deja el origen en el log. */
    private ResponseEntity<Object> invalidValue(Exception ex, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        log.warn("Rechazo del framework sin código propio: code={} requestId={} firebaseUid={} origen={}",
                ErrorCode.REQUEST_INVALID_VALUE, requestId, firebaseUid(request), RedactedException.origin(ex));
        return respond(problem(ErrorCode.REQUEST_INVALID_VALUE, null), requestId);
    }

    /** Responde el problema del código, con el mensaje dado o el del catálogo. */
    private ResponseEntity<Object> reject(ErrorCode code, String detail, WebRequest request) {
        return reject(problem(code, detail), request);
    }

    /** Registra el rechazo con su código, el identificador de la petición y la identidad, y lo responde. */
    private ResponseEntity<Object> reject(ProblemDetail problem, WebRequest request) {
        var requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        // Un 5xx significa que alguien debe actuar (por ejemplo, una réplica que nunca llegó); los 4xx son resultados esperables del cliente.
        if (problem.getStatus() >= 500) {
            log.error("Rechazo de negocio con estado de servidor: code={} requestId={} firebaseUid={}",
                    problem.getProperties().get("code"), requestId, firebaseUid(request));
        } else {
            log.warn("Petición rechazada: code={} requestId={} firebaseUid={}",
                    problem.getProperties().get("code"), requestId, firebaseUid(request));
        }
        return respond(problem, requestId);
    }

    private static List<FieldProblem> fieldProblems(MethodArgumentNotValidException ex) {
        var target = ex.getBindingResult().getTarget();
        var owner = target != null ? target.getClass().getSimpleName() : ex.getBindingResult().getObjectName();
        var firstPerField = new LinkedHashMap<String, FieldProblem>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            // Una regla del dominio ejecutada en el borde trae su propio código y mensaje; las demás salen del catálogo.
            var domainCode = domainRuleCode(fe);
            var code = domainCode != null ? domainCode : ErrorCatalog.fieldCode(owner + "." + fe.getField() + "." + fe.getCode());
            var message = domainCode != null ? fe.getDefaultMessage() : ErrorCatalog.fieldMessage(code);
            firstPerField.putIfAbsent(fe.getField(), new FieldProblem(fe.getField(), code, message));
        });
        return List.copyOf(firstPerField.values());
    }

    /** @return el código que una regla del dominio adjuntó a la violación, o {@code null} para cualquier otra restricción */
    private static ErrorCode domainRuleCode(FieldError fe) {
        if (fe.contains(ConstraintViolation.class)
                && fe.unwrap(ConstraintViolation.class) instanceof HibernateConstraintViolation<?> violation) {
            return violation.getDynamicPayload(ErrorCode.class);
        }
        return null;
    }

}
