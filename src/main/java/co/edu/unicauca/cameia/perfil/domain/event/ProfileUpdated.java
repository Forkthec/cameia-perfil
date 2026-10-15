package co.edu.unicauca.cameia.perfil.domain.event;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho de negocio «cambió un Perfil Profesional activo»: lo que Entrevista necesita para mantener su copia del perfil.
 *
 * <p>Lleva el agregado ya modificado y se convierte en la carga dentro de la misma transacción del cambio, así que el
 * agregado no vuelve a cambiar antes de leerse.</p>
 *
 * @param eventId       identificador del evento, también el {@code message_id} del mensaje
 * @param profile       perfil activo tal como queda después del cambio
 * @param updatedAt     instante del cambio en UTC, con milisegundos
 * @param correlationId correlación con la petición que originó el cambio
 */
public record ProfileUpdated(UUID eventId, ProfessionalProfile profile, Instant updatedAt, CorrelationId correlationId) {

    /** Tipo del evento; también es la clave de enrutamiento. */
    public static final String TYPE = "perfil.profesional-actualizado";

    /** Versión del contrato de la carga. */
    public static final int VERSION = 1;

    /**
     * Comprueba los componentes.
     *
     * @throws NullPointerException     si falta algún componente
     * @throws IllegalArgumentException si el perfil no está activo: solo un perfil activo se anuncia
     */
    public ProfileUpdated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(correlationId, "correlationId");
        if (profile.getStatus() != ProfileStatus.COMPLETED) {
            throw new IllegalArgumentException("Solo se anuncia un perfil activo");
        }
    }
}
