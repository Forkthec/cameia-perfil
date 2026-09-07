package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Única frontera donde las excepciones se traducen a HTTP (reglas de código, sección 7, regla 5).
 * Usa ProblemDetail de RFC 9457 vía spring.mvc.problemdetails.enabled=true.
 * El formato final de campos como codigoCameia y correlationId queda pendiente de API-TBD-14.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail handleProfileNotFound(ProfileNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Perfil no encontrado");
        return problem;
    }

    @ExceptionHandler(ProfileAlreadyExistsException.class)
    ProblemDetail handleProfileAlreadyExists(ProfileAlreadyExistsException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Perfil ya existe");
        return problem;
    }
}
