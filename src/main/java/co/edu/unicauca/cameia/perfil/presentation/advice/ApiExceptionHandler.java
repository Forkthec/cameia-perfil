package co.edu.unicauca.cameia.perfil.presentation.advice;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Única frontera donde las excepciones se traducen a HTTP (reglas de código, sección 7, regla 5).
 *
 * <p>Está deliberadamente vacío. La sección 8.2 del plan de CM-102 pide dejar el sitio preparado
 * "sin manejar excepciones que todavía no existen": cuando llegue el primer controlador, sus
 * errores ya tienen dónde colgarse en formato RFC 7807 en vez de inventar uno propio a las
 * carreras.
 *
 * <p>Al heredar de {@link ResponseEntityExceptionHandler} y con
 * {@code spring.mvc.problemdetails.enabled=true}, los errores que Spring ya conoce —validación de
 * {@code @Valid}, método no soportado, cuerpo ilegible— se devuelven como {@code ProblemDetail}
 * sin escribir una línea.
 *
 * <p>Lo que se agrega aquí conforme avancen las Historias de Usuario: un método por familia de
 * error de negocio, traduciendo las excepciones de {@code domain.exception} a su código HTTP y a
 * los campos {@code codigoCameia}, {@code correlationId} y {@code errores} que propone la sección
 * 7.1 de las reglas de código. El formato final depende de {@code API-TBD-14}, que sigue abierto.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
}
