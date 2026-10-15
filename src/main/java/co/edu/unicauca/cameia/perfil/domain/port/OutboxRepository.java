package co.edu.unicauca.cameia.perfil.domain.port;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tabla de salida (outbox): eventos que se guardan junto con el cambio que los origina y se publican después.
 *
 * <p>Guardar el evento en la misma transacción que el perfil evita que un fallo del broker pierda eventos; el relevo los
 * publica más tarde y, al confirmarse, borra la carga para no conservar datos de más.</p>
 */
public interface OutboxRepository {

    /**
     * Guarda un evento pendiente; debe correr dentro de la transacción del cambio que lo produjo.
     *
     * @param event evento que se guarda
     */
    void append(OutboundEvent event);

    /**
     * Guarda el evento de perfil actualizado como pendiente, con su carga del contrato v1, en la transacción del cambio.
     *
     * @param event evento con el perfil activo ya modificado
     * @return el evento tal como quedó guardado, listo para el intento posterior al commit
     */
    OutboundEvent appendProfileUpdated(ProfileUpdated event);

    /**
     * Lee los pendientes más antiguos y los bloquea, de modo que otra instancia del relevo salte esas filas.
     *
     * @param limit cantidad máxima de eventos
     * @return los pendientes por orden de creación, como máximo {@code limit}; exige una transacción abierta
     */
    List<OutboundEvent> lockPending(int limit);

    /**
     * Marca el evento como publicado y borra su carga (datos mínimos).
     *
     * @param id          identificador del evento
     * @param publishedAt instante en que el broker confirmó la publicación
     */
    void markPublished(UUID id, Instant publishedAt);

    /**
     * Suma un intento fallido a un evento pendiente.
     *
     * @param id identificador del evento
     * @return los intentos del evento después de sumar este; 0 si el evento ya no está pendiente
     */
    int addAttempt(UUID id);

    /**
     * Cuenta los eventos que siguen sin publicar.
     *
     * @return cantidad de pendientes
     */
    long countPending();
}
