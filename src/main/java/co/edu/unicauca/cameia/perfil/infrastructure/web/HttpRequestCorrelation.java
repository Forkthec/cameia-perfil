package co.edu.unicauca.cameia.perfil.infrastructure.web;

import co.edu.unicauca.cameia.perfil.domain.port.RequestCorrelation;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Lee el {@code X-Request-Id} de la petición HTTP que atiende el hilo actual.
 *
 * <p>No guarda estado: la petición se consulta en cada llamada, así que sirve con cualquier número de instancias. No valida el
 * valor; esa regla la aplica el identificador de correlación del dominio.</p>
 */
@Component
class HttpRequestCorrelation implements RequestCorrelation {

    /** Encabezado que el Gateway pone en cada petición. */
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** {@inheritDoc} */
    @Override
    public Optional<String> currentRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.ofNullable(attributes.getRequest().getHeader(REQUEST_ID_HEADER));
        }
        return Optional.empty();
    }
}
