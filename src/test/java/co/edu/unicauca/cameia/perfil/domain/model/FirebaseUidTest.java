package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseUidTest {

    @Test
    @DisplayName("Un UID de 128 caracteres es válido")
    void create_shouldKeepValue_whenLengthIsMax() {
        var uid = new FirebaseUid("a".repeat(FirebaseUid.MAX_LENGTH));

        assertThat(uid.value()).hasSize(128);
    }

    @Test
    @DisplayName("Un UID de 129 caracteres se rechaza")
    void create_shouldThrow_whenLengthExceedsMax() {
        assertThatThrownBy(() -> new FirebaseUid("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Un UID en blanco se rechaza")
    void create_shouldThrow_whenBlank(String value) {
        assertThatThrownBy(() -> new FirebaseUid(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un UID nulo se rechaza")
    void create_shouldThrow_whenNull() {
        assertThatThrownBy(() -> new FirebaseUid(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("La identidad del Gateway en blanco se rechaza como identidad requerida")
    void required_shouldThrowIdentityRequired_whenBlank(String raw) {
        assertThatThrownBy(() -> FirebaseUid.required(raw)).isInstanceOf(IdentityRequiredException.class);
    }

    @Test
    @DisplayName("La identidad del Gateway ausente o de 129 caracteres se rechaza como identidad requerida")
    void required_shouldThrowIdentityRequired_whenNullOrTooLong() {
        assertThatThrownBy(() -> FirebaseUid.required(null)).isInstanceOf(IdentityRequiredException.class);
        assertThatThrownBy(() -> FirebaseUid.required("a".repeat(129))).isInstanceOf(IdentityRequiredException.class);
    }

    @Test
    @DisplayName("La identidad del Gateway de 128 caracteres es válida")
    void required_shouldReturnUid_whenLengthIsMax() {
        assertThat(FirebaseUid.required("a".repeat(128)).value()).hasSize(128);
    }
}
