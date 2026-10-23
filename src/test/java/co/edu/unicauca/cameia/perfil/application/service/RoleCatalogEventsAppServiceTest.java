package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.event.OutboundEvent;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;
import co.edu.unicauca.cameia.perfil.domain.port.OutboxRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Anota la instantánea del catálogo en la tabla de salida solo si no hay otra pendiente. */
@ExtendWith(MockitoExtension.class)
class RoleCatalogEventsAppServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T12:00:00.123456Z"), ZoneOffset.UTC);
    private static final String TYPE = "perfil.catalogo-roles-actualizado";

    @Mock OutboxRepository outbox;
    @Mock ProfessionalRoleRepository roles;
    @Mock OutboxRelayAppService relay;

    RoleCatalogEventsAppService service;

    @BeforeEach
    void setUp() {
        service = new RoleCatalogEventsAppService(outbox, roles, relay, CLOCK);
    }

    private static OutboundEvent stored() {
        return new OutboundEvent(UUID.randomUUID(), TYPE, 1, RoleCatalogUpdated.AGGREGATE_ID, "{}", "perfil-catalogo-00000000",
                Instant.parse("2026-10-24T12:00:00.123Z"));
    }

    @Test
    @DisplayName("Si otra instancia tiene el candado no comprueba pendientes, no lee el catálogo ni escribe nada")
    void announceCatalog_shouldDoNothing_whenAnotherInstanceHoldsTheLock() {
        when(outbox.tryLockAnnouncement(TYPE)).thenReturn(false);

        service.announceCatalog();

        verify(outbox, never()).existsPendingOfType(any());
        verify(outbox, never()).saveRoleCatalog(any());
        verifyNoInteractions(roles, relay);
    }

    @Test
    @DisplayName("Con uno pendiente no lee el catálogo ni escribe nada")
    void announceCatalog_shouldDoNothing_whenOneIsAlreadyPending() {
        when(outbox.tryLockAnnouncement(TYPE)).thenReturn(true);
        when(outbox.existsPendingOfType(TYPE)).thenReturn(true);

        service.announceCatalog();

        verifyNoInteractions(roles, relay);
        verify(outbox, never()).saveRoleCatalog(any());
    }

    @Test
    @DisplayName("Sin pendiente guarda la instantánea completa y programa su envío tras el commit")
    void announceCatalog_shouldSaveSnapshotAndRelay_whenNoneIsPending() {
        var frontend = new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000001"), "Desarrollador Frontend",
                "Frontend Developer", true);
        var backend = new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Desarrollador Backend",
                "Backend Developer", false);
        var saved = stored();
        when(outbox.tryLockAnnouncement(TYPE)).thenReturn(true);
        when(outbox.existsPendingOfType(TYPE)).thenReturn(false);
        when(roles.findAllWithBothLanguages()).thenReturn(List.of(frontend, backend));
        when(outbox.saveRoleCatalog(any())).thenReturn(saved);

        service.announceCatalog();

        var captor = ArgumentCaptor.forClass(RoleCatalogUpdated.class);
        verify(outbox).saveRoleCatalog(captor.capture());
        var event = captor.getValue();
        assertThat(event.roles()).containsExactly(frontend, backend);
        assertThat(event.updatedAt()).isEqualTo(Instant.parse("2026-10-24T12:00:00.123Z"));
        assertThat(event.correlationId().value()).isEqualTo("perfil-catalogo-" + event.eventId().toString().substring(0, 8));
        verify(relay).relayAfterCommit(saved);
    }

    @Test
    @DisplayName("Un rol sin nombre en inglés viaja con el español como nombre en inglés")
    void announceCatalog_shouldUseSpanishName_whenEnglishNameIsNull() {
        when(outbox.tryLockAnnouncement(TYPE)).thenReturn(true);
        when(outbox.existsPendingOfType(TYPE)).thenReturn(false);
        when(roles.findAllWithBothLanguages()).thenReturn(List.of(
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000001"), "Desarrollador Frontend", null, true)));
        when(outbox.saveRoleCatalog(any())).thenReturn(stored());

        service.announceCatalog();

        var captor = ArgumentCaptor.forClass(RoleCatalogUpdated.class);
        verify(outbox).saveRoleCatalog(captor.capture());
        assertThat(captor.getValue().roles().getFirst().nameEn()).isEqualTo("Desarrollador Frontend");
    }

    @Test
    @DisplayName("Con el catálogo vacío falla antes de escribir: no se publica una instantánea inválida")
    void announceCatalog_shouldFailWithoutWriting_whenCatalogIsEmpty() {
        when(outbox.tryLockAnnouncement(TYPE)).thenReturn(true);
        when(outbox.existsPendingOfType(TYPE)).thenReturn(false);
        when(roles.findAllWithBothLanguages()).thenReturn(List.of());

        assertThatThrownBy(() -> service.announceCatalog()).isInstanceOf(IllegalArgumentException.class);

        verify(outbox, never()).saveRoleCatalog(any());
        verifyNoInteractions(relay);
    }
}
