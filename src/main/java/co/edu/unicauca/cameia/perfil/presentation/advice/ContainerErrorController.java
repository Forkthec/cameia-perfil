package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.RedactedException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.net.URI;

/**
 * Responde con la forma común los errores que el contenedor reporta con {@code sendError} y que
 * Spring Boot mostraría con su cuerpo por defecto ({@code timestamp}, {@code error}, {@code path}):
 * por ejemplo un {@code TRACE}. También es la red de seguridad de cualquier error que ocurra en un
 * filtro, antes de los controladores.
 *
 * <p>Reemplaza al controlador de errores por defecto de Spring Boot. No es un endpoint del contrato:
 * está oculto en OpenAPI y una petición directa a {@code /error} responde 404 como cualquier ruta inexistente.</p>
 */
@Hidden
@RestController
class ContainerErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ContainerErrorController.class);

    /**
     * Atiende el despacho de error del contenedor.
     *
     * @param request petición de error, con los atributos que dejó el contenedor
     * @return el problema común del estado original, con {@code code}, {@code requestId} y {@code instance}
     */
    @RequestMapping("/error")
    ResponseEntity<Object> error(HttpServletRequest request) {
        var original = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        var code = codeFor(original);
        var web = new ServletWebRequest(request);
        var requestId = ProblemResponses.requestId(web);
        // Si el manejador de la aplicación ya registró este error, el despacho solo responde: una petición, un registro.
        if (!ProblemResponses.alreadyLogged(web)) {
            var firebaseUid = ProblemResponses.firebaseUid(web);
            if (code == ErrorCode.INTERNAL_ERROR) {
                // Un estado sin código propio: alguien debe darle uno. Se registra el estado y el origen de la
                // excepción que informó el contenedor, nunca la ruta ni valores.
                log.error("Error del contenedor sin código en el catálogo: estado={} origen={} requestId={} firebaseUid={}",
                        original, origin(request), requestId, firebaseUid);
            } else {
                log.warn("Petición rechazada: code={} requestId={} firebaseUid={}", code, requestId, firebaseUid);
            }
        }
        var problem = ProblemResponses.problem(code, null);
        var instance = originalUri(request);
        if (instance != null) {
            problem.setInstance(instance);
        }
        return ProblemResponses.respond(problem, requestId);
    }

    /**
     * Traduce el estado original que reportó el contenedor al código del catálogo.
     *
     * @param status estado original, o {@code null} si la petición llegó directo a {@code /error}
     * @return el código propio del estado; 400 y 413 son datos del cliente; todo lo demás es el genérico
     */
    static ErrorCode codeFor(Integer status) {
        if (status == null) {
            return ErrorCode.ROUTE_NOT_FOUND;
        }
        return switch (status) {
            case 404 -> ErrorCode.ROUTE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.MEDIA_TYPE_NOT_ACCEPTABLE;
            case 415 -> ErrorCode.MEDIA_TYPE_NOT_ALLOWED;
            // Un 400 del contenedor es un dato del cliente que no se pudo leer (por ejemplo, un cuerpo troceado con
            // un trozo mal formado): sale como cuerpo ilegible, igual que un JSON roto.
            case 400 -> ErrorCode.REQUEST_BODY_INVALID_FORMAT;
            // Un 413 del contenedor es el mismo caso que el tope de tamaño de Spring: un cuerpo demasiado grande.
            case 413 -> ErrorCode.REQUEST_BODY_TOO_LARGE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    /** @return la clase y el método donde se lanzó la excepción que informó el contenedor, o {@code -} si no informó ninguna */
    private static String origin(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable cause
                ? RedactedException.origin(cause) : "-";
    }

    /** @return la ruta original como URI, o {@code null} si falta o no es un URI válido (por ejemplo, trae {@code %zz}) */
    private static URI originalUri(HttpServletRequest request) {
        var uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (uri == null) {
            return null;
        }
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
