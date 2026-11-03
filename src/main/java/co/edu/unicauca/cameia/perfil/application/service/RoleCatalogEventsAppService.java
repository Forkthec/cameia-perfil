package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Anuncia el catálogo de roles profesionales para que otros servicios mantengan su copia.
 *
 * <p>El catálogo solo cambia con una migración y un despliegue, así que se anuncia al arrancar. La instantánea se guarda
 * en la tabla de salida y se publica después del commit; lo que falle lo recoge el Job de relevo. Si ya hay una instantánea
 * del catálogo sin publicar, no se agrega otra: arranques seguidos con el broker caído dejan una sola fila pendiente.
 * Un candado de la base, que dura lo que la transacción, reduce el caso de varias instancias que arrancan a la vez: la que llega
 * mientras otra anota se salta. No garantiza una sola instantánea, porque una instancia que llega justo después encuentra la
 * fila ya publicada.</p>
 */
@Service
public class RoleCatalogEventsAppService {

    private static final Logger log = LoggerFactory.getLogger(RoleCatalogEventsAppService.class);

    private final OutboxRepository outbox;
    private final ProfessionalRoleRepository roles;
    private final OutboxRelayAppService relay;
    /** Reloj en UTC: da el instante {@code actualizadoEn} de la instantánea. */
    private final Clock clock;

    RoleCatalogEventsAppService(OutboxRepository outbox, ProfessionalRoleRepository roles, OutboxRelayAppService relay,
            Clock clock) {
        this.outbox = outbox;
        this.roles = roles;
        this.relay = relay;
        this.clock = clock;
    }

    /**
     * Guarda la instantánea del catálogo y deja programado su envío para después del commit, salvo que ya haya una sin
     * publicar.
     *
     * @throws IllegalArgumentException si el catálogo está vacío o supera el máximo del contrato
     */
    @Transactional
    public void announceCatalog() {
        // Varias instancias que arrancan a la vez anotarían una instantánea cada una: solo la que toma el candado sigue.
        if (!outbox.tryLockAnnouncement(RoleCatalogUpdated.TYPE)) {
            log.info("Otra instancia está anotando el catálogo de roles; esta no agrega otra instantánea");
            return;
        }
        if (outbox.existsPendingOfType(RoleCatalogUpdated.TYPE)) {
            log.info("El catálogo de roles ya tiene una instantánea pendiente de publicar; no se agrega otra");
            return;
        }
        var eventId = UUID.randomUUID();
        // No hay petición HTTP que lo origine: la correlación se deriva del propio evento.
        var correlation = new CorrelationId("perfil-catalogo-" + eventId.toString().substring(0, 8));
        var event = new RoleCatalogUpdated(eventId, roles.findAllWithBothLanguages(),
                clock.instant().truncatedTo(ChronoUnit.MILLIS), correlation);
        relay.relayAfterCommit(outbox.saveRoleCatalog(event));
        // Solo la cantidad: los nombres de los roles no se escriben en el log.
        log.info("Instantánea del catálogo de roles anotada para publicar [eventId={}, roles={}]", eventId, event.roles().size());
    }
}
