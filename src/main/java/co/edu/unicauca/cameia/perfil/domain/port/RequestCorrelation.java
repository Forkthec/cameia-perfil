package co.edu.unicauca.cameia.perfil.domain.port;

import java.util.Optional;

/**
 * Da el identificador de la petición que está en curso, para unir un evento con la petición que lo originó.
 *
 * <p>El dominio no sabe de HTTP: fuera de una petición (por ejemplo en el Job de relevo) no hay identificador.</p>
 */
public interface RequestCorrelation {

    /**
     * Lee el identificador de la petición en curso sin validarlo: la regla de qué valores son seguros es del dominio.
     *
     * @return el {@code X-Request-Id} tal como llegó, o vacío si no hay petición o no trae el encabezado
     */
    Optional<String> currentRequestId();
}
