package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Construye las respuestas de error de Perfil con la forma común.
 *
 * <p>Todo problema lleva {@code code} y {@code requestId} en el cuerpo, el encabezado
 * {@code X-Request-Id} y el tipo {@code application/problem+json} con {@code charset=UTF-8}.
 * El manejador decide qué código corresponde a cada excepción; esta clase solo arma la respuesta.</p>
 */
final class ProblemResponses {

    /** Encabezado con el identificador de la petición, recibido del Gateway o generado aquí. */
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Encabezado con la identidad del Usuario que pone el Gateway. */
    static final String IDENTITY_HEADER = "X-User-Id";
    private static final String NO_UID = "-";

    private static final MediaType PROBLEM_JSON_UTF8 =
            new MediaType("application", "problem+json", StandardCharsets.UTF_8);
    // Solo se repite un identificador corto y sin caracteres de control ni de marcado.
    private static final Pattern REQUEST_ID_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private ProblemResponses() { }

    /**
     * Arma el problema con el estado y el título del catálogo.
     *
     * @param code   código del error
     * @param detail mensaje para la persona; si es {@code null} se usa el del catálogo
     * @return el problema con su {@code code}
     */
    static ProblemDetail problem(ErrorCode code, String detail) {
        var definition = ErrorCatalog.of(code);
        var problem = ProblemDetail.forStatusAndDetail(definition.status(),
                detail != null ? detail : definition.detail());
        problem.setTitle(definition.title());
        problem.setProperty("code", code);
        return problem;
    }

    /**
     * Agrega el identificador de la petición al cuerpo y al encabezado, y declara el charset.
     *
     * @param problem   problema ya armado
     * @param requestId identificador de la petición
     * @return la respuesta lista para enviar
     */
    static ResponseEntity<Object> respond(ProblemDetail problem, String requestId) {
        problem.setProperty("requestId", requestId);
        return ResponseEntity.status(problem.getStatus()).contentType(PROBLEM_JSON_UTF8)
                .header(REQUEST_ID_HEADER, requestId).body(problem);
    }

    /**
     * Agrega a la respuesta los encabezados que puso el framework, como {@code Allow} o {@code Accept}.
     * Si los dos traen el mismo encabezado, gana el de la respuesta.
     *
     * @param response respuesta armada
     * @param extra    encabezados del framework
     * @return una respuesta nueva con los dos grupos de encabezados
     */
    static ResponseEntity<Object> withHeaders(ResponseEntity<Object> response, HttpHeaders extra) {
        var headers = new HttpHeaders();
        headers.putAll(extra);
        headers.putAll(response.getHeaders());
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    /**
     * Devuelve el identificador de la petición: el recibido si es válido, o uno nuevo.
     *
     * @param received valor del encabezado {@code X-Request-Id}, o {@code null}
     * @return un identificador seguro para devolver y registrar
     */
    static String requestId(String received) {
        return received != null && REQUEST_ID_FORMAT.matcher(received).matches()
                ? received : UUID.randomUUID().toString();
    }

    /**
     * Devuelve la identidad del Usuario para el registro, nunca para la respuesta.
     *
     * @param request petición en curso
     * @return el valor de {@code X-User-Id}, o {@code -} si falta, está en blanco, mide más de 128
     *         caracteres o trae caracteres de control (que permitirían falsear líneas del registro)
     */
    static String firebaseUid(WebRequest request) {
        var uid = request.getHeader(IDENTITY_HEADER);
        if (uid == null || uid.isBlank() || uid.length() > FirebaseUid.MAX_LENGTH) {
            return NO_UID;
        }
        return uid.chars().anyMatch(Character::isISOControl) ? NO_UID : uid;
    }
}
