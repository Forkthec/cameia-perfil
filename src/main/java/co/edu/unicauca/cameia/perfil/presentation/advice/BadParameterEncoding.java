package co.edu.unicauca.cameia.perfil.presentation.advice;

import java.util.Locale;
import java.util.Set;

/**
 * Reconoce el rechazo del contenedor ante un parámetro mal codificado ({@code %zz}).
 *
 * <p>La excepción es de Tomcat; se compara el nombre de su clase para que la capa de presentación
 * no dependa del contenedor. Se busca también en la cadena de causas por si un componente la envolvió.</p>
 */
final class BadParameterEncoding {

    private static final String TOMCAT_CLASS = "org.apache.tomcat.util.http.InvalidParameterException";
    private static final String FORM_MEDIA_TYPE = "application/x-www-form-urlencoded";
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    /** Tope de causas que se recorren: protege de una cadena circular. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private BadParameterEncoding() { }

    /**
     * Indica si el fallo es el rechazo de Tomcat a un parámetro mal codificado.
     *
     * @param error excepción recibida por el manejador, o {@code null}
     * @return {@code true} si la excepción o alguna de sus causas es el rechazo de Tomcat
     */
    static boolean matches(Throwable error) {
        var current = error;
        for (var depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (TOMCAT_CLASS.equals(current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Indica si el parámetro malo venía del cuerpo de un formulario y no de la consulta.
     *
     * @param method      método HTTP de la petición, o {@code null}
     * @param contentType encabezado {@code Content-Type}, o {@code null}
     * @return {@code true} si el método admite cuerpo y el tipo es un formulario (sin distinguir mayúsculas)
     */
    static boolean fromFormBody(String method, String contentType) {
        return method != null && BODY_METHODS.contains(method.toUpperCase(Locale.ROOT))
                && contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(FORM_MEDIA_TYPE);
    }
}
