package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.RemoveBirthDateCommand;
import co.edu.unicauca.cameia.perfil.application.command.ReplicateBirthDateCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException.Reason;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.port.BirthDateReplica;
import co.edu.unicauca.cameia.perfil.domain.port.ProcessedMessageInbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Mantiene la réplica de la fecha de nacimiento al día con los eventos de cuenta.
 *
 * <p>Los eventos llegan al menos una vez y en cualquier orden. Cada mensaje se valida primero y luego se anota en el Inbox y se
 * aplica en la misma transacción, así que una reentrega no tiene efecto. Un segundo evento de creación del mismo Usuario conserva
 * la primera fecha guardada y borrar una fila inexistente no falla. El contenido que incumple el contrato se rechaza con una
 * razón estable antes de tocar el Inbox, de modo que el mensaje se puede reenviar una vez corregido. Ni la fecha de nacimiento
 * ni ningún otro dato del evento se escribe en el log.</p>
 */
@Service
public class BirthDateReplicaAppService {

    static final String ACCOUNT_CREATED = "cuenta.creada";
    static final String ACCOUNT_DELETED = "cuenta.eliminada";

    private static final Logger log = LoggerFactory.getLogger(BirthDateReplicaAppService.class);

    private final BirthDateReplica replica;
    private final ProcessedMessageInbox inbox;
    private final Clock clock;

    BirthDateReplicaAppService(BirthDateReplica replica, ProcessedMessageInbox inbox, Clock clock) {
        this.replica = replica;
        this.inbox = inbox;
        this.clock = clock;
    }

    /**
     * Replica la fecha de nacimiento de un evento de cuenta creada.
     *
     * @param command identificador del mensaje, identidad y fecha de nacimiento tomados del evento
     * @throws InvalidAccountEventException si el identificador del mensaje falta o no es un UUID, la identidad falta, está en
     *         blanco o mide más de 128 caracteres, o la fecha de nacimiento falta o es posterior a hoy en UTC
     */
    @Transactional
    public void recordAccountCreated(ReplicateBirthDateCommand command) {
        UUID messageId = messageId(command.messageId());
        FirebaseUid userId = userId(command.userId());
        LocalDate birthDate = birthDate(command.birthDate());
        if (!inbox.registerIfAbsent(messageId, ACCOUNT_CREATED, clock.instant())) {
            log.info("Mensaje ya procesado; se ignora [messageId={}]", messageId);
            return;
        }
        if (replica.saveIfAbsent(userId, birthDate)) {
            log.info("Fecha de nacimiento replicada [firebaseUid={}, messageId={}]", userId.value(), messageId);
        } else {
            log.info("Fecha ya replicada; el evento se ignora [firebaseUid={}, messageId={}]", userId.value(), messageId);
        }
    }

    /**
     * Borra la fecha de nacimiento de un evento de cuenta eliminada.
     *
     * @param command identificador del mensaje e identidad tomados del evento
     * @throws InvalidAccountEventException si el identificador del mensaje o la identidad no son válidos
     */
    @Transactional
    public void recordAccountDeleted(RemoveBirthDateCommand command) {
        UUID messageId = messageId(command.messageId());
        FirebaseUid userId = userId(command.userId());
        if (!inbox.registerIfAbsent(messageId, ACCOUNT_DELETED, clock.instant())) {
            log.info("Mensaje ya procesado; se ignora [messageId={}]", messageId);
            return;
        }
        if (replica.delete(userId)) {
            log.info("Fecha de nacimiento borrada [firebaseUid={}, messageId={}]", userId.value(), messageId);
        } else {
            log.info("No había fecha que borrar [firebaseUid={}, messageId={}]", userId.value(), messageId);
        }
    }

    /** Convierte el identificador de mensaje del productor; si falta o no es un UUID canónico, incumple el contrato. */
    private static UUID messageId(String raw) {
        if (raw == null) {
            throw new InvalidAccountEventException(Reason.MESSAGE_ID_INVALID);
        }
        try {
            UUID parsed = UUID.fromString(raw);
            // UUID.fromString acepta formas no canónicas como «1-1-1-1-1»: se exige la forma de 36 caracteres.
            if (!parsed.toString().equals(raw.toLowerCase(Locale.ROOT))) {
                throw new InvalidAccountEventException(Reason.MESSAGE_ID_INVALID);
            }
            return parsed;
        } catch (IllegalArgumentException malformed) {
            throw new InvalidAccountEventException(Reason.MESSAGE_ID_INVALID);
        }
    }

    /** Valida la identidad con el mismo límite del objeto de valor y la traduce a una violación del contrato. */
    private static FirebaseUid userId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > FirebaseUid.MAX_LENGTH) {
            throw new InvalidAccountEventException(Reason.USER_ID_INVALID);
        }
        return new FirebaseUid(raw);
    }

    /** La fecha de nacimiento debe existir y no puede ser posterior a hoy en UTC. */
    private LocalDate birthDate(LocalDate raw) {
        if (raw == null) {
            throw new InvalidAccountEventException(Reason.BIRTH_DATE_REQUIRED);
        }
        if (raw.isAfter(LocalDate.now(clock))) {
            throw new InvalidAccountEventException(Reason.BIRTH_DATE_IN_THE_FUTURE);
        }
        return raw;
    }
}
