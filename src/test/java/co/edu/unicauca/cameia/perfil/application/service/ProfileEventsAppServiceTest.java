package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.domain.port.RequestCorrelation;
import co.edu.unicauca.cameia.perfil.support.ActiveProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Decide si un cambio se anuncia y deja el evento guardado en la transacción del cambio. */
@ExtendWith(MockitoExtension.class)
class ProfileEventsAppServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00.123456Z"), ZoneOffset.UTC);

    @Mock OutboxRepository outbox;
    @Mock OutboxRelayAppService relay;
    @Mock RequestCorrelation requestCorrelation;

    ProfileEventsAppService service;

    @BeforeEach
    void setUp() {
        service = new ProfileEventsAppService(outbox, relay, requestCorrelation, CLOCK);
    }

    private ProfileUpdated announce() {
        var stored = new OutboundEvent(UUID.randomUUID(), ProfileUpdated.TYPE, ProfileUpdated.VERSION, ActiveProfiles.PROFILE_ID,
                "{}", "req-1", Instant.parse("2026-10-15T12:00:00.123Z"));
        when(outbox.appendProfileUpdated(any())).thenReturn(stored);
        service.announceIfCompleted(ActiveProfiles.example());
        var captor = ArgumentCaptor.forClass(ProfileUpdated.class);
        verify(outbox).appendProfileUpdated(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Un perfil activo se guarda como evento y se programa su envío")
    void announceIfCompleted_shouldAppendAndRelay_whenProfileIsCompleted() {
        var profile = ActiveProfiles.example();
        var stored = new OutboundEvent(UUID.randomUUID(), ProfileUpdated.TYPE, ProfileUpdated.VERSION, ActiveProfiles.PROFILE_ID,
                "{}", "req-1", Instant.parse("2026-10-15T12:00:00.123Z"));
        when(outbox.appendProfileUpdated(any())).thenReturn(stored);

        service.announceIfCompleted(profile);

        var captor = ArgumentCaptor.forClass(ProfileUpdated.class);
        verify(outbox).appendProfileUpdated(captor.capture());
        assertThat(captor.getValue().profile()).isSameAs(profile);
        verify(relay).relayAfterCommit(stored);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ProfileStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Un perfil que no está activo no se anuncia")
    void announceIfCompleted_shouldDoNothing_whenProfileIsNotCompleted(ProfileStatus status) {
        service.announceIfCompleted(ActiveProfiles.withStatus(status));

        verifyNoInteractions(outbox, relay);
    }

    @Test
    @DisplayName("Un X-Request-Id válido es la correlación del evento")
    void announceIfCompleted_shouldUseRequestId_whenItIsValid() {
        when(requestCorrelation.currentRequestId()).thenReturn(Optional.of("req-123"));

        assertThat(announce().correlationId().value()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("Sin X-Request-Id la correlación es el identificador del evento")
    void announceIfCompleted_shouldUseEventId_whenRequestIdIsMissing() {
        when(requestCorrelation.currentRequestId()).thenReturn(Optional.empty());

        var event = announce();

        assertThat(event.correlationId().value()).isEqualTo(event.eventId().toString());
    }

    @Test
    @DisplayName("Un X-Request-Id hostil se descarta y se usa el identificador del evento")
    void announceIfCompleted_shouldUseEventId_whenRequestIdIsHostile() {
        when(requestCorrelation.currentRequestId()).thenReturn(Optional.of("<script>alert(1)</script>"));

        var event = announce();

        assertThat(event.correlationId().value()).isEqualTo(event.eventId().toString());
    }

    @Test
    @DisplayName("El instante del evento se trunca a milisegundos")
    void announceIfCompleted_shouldTruncateToMilliseconds_whenClockHasMicros() {
        when(requestCorrelation.currentRequestId()).thenReturn(Optional.empty());

        assertThat(announce().updatedAt()).isEqualTo(Instant.parse("2026-10-15T12:00:00.123Z"));
    }

    @Test
    @DisplayName("Si la tabla de salida falla el error se propaga para revertir el cambio y no se programa el envío")
    void announceIfCompleted_shouldPropagate_whenOutboxFails() {
        when(requestCorrelation.currentRequestId()).thenReturn(Optional.empty());
        when(outbox.appendProfileUpdated(any())).thenThrow(new IllegalStateException("x"));
        var profile = ActiveProfiles.example();

        assertThatThrownBy(() -> service.announceIfCompleted(profile)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(relay);
    }
}
