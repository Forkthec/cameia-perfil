package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.presentation.dto.CompletionErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Única frontera donde las excepciones se traducen a HTTP (reglas de código, sección 7, regla 5).
 * Usa ProblemDetail de RFC 9457 vía spring.mvc.problemdetails.enabled=true.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail handleProfileNotFound(ProfileNotFoundException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Perfil no encontrado"); return p;
    }

    @ExceptionHandler(ProfileAlreadyExistsException.class)
    ProblemDetail handleProfileAlreadyExists(ProfileAlreadyExistsException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Perfil ya existe"); return p;
    }

    @ExceptionHandler(IncompleteProfileException.class)
    ResponseEntity<CompletionErrorResponse> handleIncompleteProfile(IncompleteProfileException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new CompletionErrorResponse(ex.getMissingRequirements()));
    }

    @ExceptionHandler(ProfileAlreadyCompletedException.class)
    ProblemDetail handleProfileAlreadyCompleted(ProfileAlreadyCompletedException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Perfil ya completado"); return p;
    }

    @ExceptionHandler(ProfessionalRoleNotFoundException.class)
    ProblemDetail handleProfessionalRoleNotFound(ProfessionalRoleNotFoundException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Rol profesional no encontrado"); return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("Valor no válido"); return p;
    }

    @ExceptionHandler(MaxTargetRolesExceededException.class)
    ProblemDetail handleMaxTargetRoles(MaxTargetRolesExceededException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("Máximo de roles objetivo alcanzado"); return p;
    }

    @ExceptionHandler(DuplicateTargetRoleException.class)
    ProblemDetail handleDuplicateTargetRole(DuplicateTargetRoleException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Rol objetivo duplicado"); return p;
    }

    @ExceptionHandler(LastTargetRoleException.class)
    ProblemDetail handleLastTargetRole(LastTargetRoleException ex) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("No se puede eliminar el último rol objetivo"); return p;
    }

    /** CM-24: validación de Bean Validation (@NotNull, @NotBlank) → 400. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<String> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).toList();
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Campos inválidos: " + errores);
        p.setTitle("Solicitud inválida");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(p);
    }
}
