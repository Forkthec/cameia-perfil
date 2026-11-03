package co.edu.unicauca.cameia.perfil.domain.event;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho de negocio «el catálogo de roles profesionales queda así»: una instantánea completa, con los roles activos e
 * inactivos, para que otro servicio mantenga su copia del catálogo sin consultar a Perfil en línea.
 *
 * <p>El catálogo no pertenece a ningún perfil: el evento se guarda en la tabla de salida con el identificador de agregado
 * {@link #AGGREGATE_ID}.</p>
 *
 * @param eventId       identificador del evento, también el {@code message_id} del mensaje
 * @param roles         todos los roles del catálogo, de 1 a {@value #MAX_ROLES}
 * @param updatedAt     instante de la instantánea en UTC, con milisegundos
 * @param correlationId correlación del evento; no hay una petición HTTP que lo origine
 */
public record RoleCatalogUpdated(UUID eventId, List<ProfessionalRoleNames> roles, Instant updatedAt,
        CorrelationId correlationId) {

    /** Tipo del evento; también es la clave de enrutamiento. */
    public static final String TYPE = "perfil.catalogo-roles-actualizado";

    /** Versión del contrato de la carga. */
    public static final int VERSION = 1;

    /** Máximo de roles de una instantánea, el mismo del esquema publicado. */
    public static final int MAX_ROLES = 200;

    /** Agregado constante de la tabla de salida: el catálogo no es de un perfil. */
    public static final UUID AGGREGATE_ID = new UUID(0L, 0L);

    /**
     * Comprueba los componentes y deja una copia inmutable de los roles.
     *
     * @throws NullPointerException     si falta algún componente o hay un rol nulo
     * @throws IllegalArgumentException si la instantánea no tiene entre 1 y {@value #MAX_ROLES} roles
     */
    public RoleCatalogUpdated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(correlationId, "correlationId");
        roles = List.copyOf(roles);
        if (roles.isEmpty() || roles.size() > MAX_ROLES) {
            throw new IllegalArgumentException("La instantánea del catálogo debe tener de 1 a " + MAX_ROLES + " roles");
        }
    }
}
