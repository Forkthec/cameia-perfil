package co.edu.unicauca.cameia.perfil.domain.event;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.support.ActiveProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El hecho «cambió un perfil activo» exige todos sus componentes y un perfil en estado activo. */
class ProfileUpdatedTest {

    private static final UUID EVENT_ID = UUID.fromString("0b9f8e7d-6c5b-4a39-8271-605f4e3d2c1b");
    private static final Instant AT = Instant.parse("2026-10-15T12:00:00.123Z");
    private static final CorrelationId CORRELATION = new CorrelationId("req-123");

    private enum Missing { EVENT_ID, PROFILE, UPDATED_AT, CORRELATION_ID }

    @ParameterizedTest(name = "falta {0}")
    @EnumSource(Missing.class)
    @DisplayName("Falta un componente: se rechaza con NullPointerException")
    void constructor_shouldRejectNulls_whenAnyComponentIsMissing(Missing missing) {
        var profile = ActiveProfiles.example();

        assertThatThrownBy(() -> new ProfileUpdated(
                missing == Missing.EVENT_ID ? null : EVENT_ID,
                missing == Missing.PROFILE ? null : profile,
                missing == Missing.UPDATED_AT ? null : AT,
                missing == Missing.CORRELATION_ID ? null : CORRELATION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Un perfil que no está activo no se puede anunciar")
    void constructor_shouldReject_whenProfileIsNotCompleted() {
        var draft = ProfessionalProfile.create(new FirebaseUid("uid-1"));

        assertThatThrownBy(() -> new ProfileUpdated(EVENT_ID, draft, AT, CORRELATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El tipo y la versión son los del contrato")
    void constants_shouldMatchTheContract() {
        assertThat(ProfileUpdated.TYPE).isEqualTo("perfil.profesional-actualizado");
        assertThat(ProfileUpdated.VERSION).isEqualTo(1);
    }

    @Test
    @DisplayName("Con todos los componentes y un perfil activo se construye")
    void constructor_shouldKeepComponents_whenProfileIsCompleted() {
        var profile = ActiveProfiles.example();

        var event = new ProfileUpdated(EVENT_ID, profile, AT, CORRELATION);

        assertThat(event.profile()).isSameAs(profile);
        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.updatedAt()).isEqualTo(AT);
        assertThat(event.correlationId()).isEqualTo(CORRELATION);
    }
}
