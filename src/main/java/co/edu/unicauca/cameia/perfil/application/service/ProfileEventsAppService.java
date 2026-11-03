package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.domain.port.RequestCorrelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Anuncia los cambios de un Perfil Profesional activo para que Entrevista mantenga su copia.
 *
 * <p>El evento se guarda en la tabla de salida dentro de la misma transacción que el cambio: si la escritura se revierte,
 * el evento desaparece con ella, y si el broker está caído la escritura no se pierde. La publicación se intenta una vez
 * después del commit; lo que falle lo recoge el Job de relevo.</p>
 */
@Service
public class ProfileEventsAppService {

    private final OutboxRepository outbox;
    private final OutboxRelayAppService relay;
    private final RequestCorrelation requestCorrelation;
    /** Reloj en UTC: da el instante {@code actualizadoEn} del evento. */
    private final Clock clock;

    ProfileEventsAppService(OutboxRepository outbox, OutboxRelayAppService relay, RequestCorrelation requestCorrelation,
            Clock clock) {
        this.outbox = outbox;
        this.relay = relay;
        this.requestCorrelation = requestCorrelation;
        this.clock = clock;
    }

    /**
     * Guarda el evento del perfil si quedó activo y deja programado su envío para después del commit.
     *
     * @param profile perfil ya modificado y guardado en la transacción en curso
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción en curso
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void announceIfCompleted(ProfessionalProfile profile) {
        // Un borrador no le interesa a Entrevista: solo un perfil activo se puede usar en una entrevista.
        if (profile.getStatus() != ProfileStatus.COMPLETED) {
            return;
        }
        var eventId = UUID.randomUUID();
        // El X-Request-Id solo se usa si es seguro; si no, la correlación es el propio evento.
        var correlation = CorrelationId.fromRequestIdOrElse(requestCorrelation.currentRequestId().orElse(null), eventId);
        var event = new ProfileUpdated(eventId, profile, clock.instant().truncatedTo(ChronoUnit.MILLIS), correlation);
        relay.relayAfterCommit(outbox.appendProfileUpdated(event));
    }
}
