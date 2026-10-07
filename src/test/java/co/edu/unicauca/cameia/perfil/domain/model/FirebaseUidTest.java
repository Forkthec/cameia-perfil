package co.edu.unicauca.cameia.perfil.domain.model;

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
}
