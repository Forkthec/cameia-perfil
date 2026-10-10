package co.edu.unicauca.cameia.perfil.infrastructure.messaging.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** El identificador del mensaje solo llega al registro si tiene una forma segura. */
class EventLogValuesTest {

    @Test
    @DisplayName("Sin identificador se registra un guion")
    void safeMessageId_shouldReturnDash_whenMessageIdIsNull() {
        assertThat(EventLogValues.safeMessageId(null)).isEqualTo("-");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111-1111-4111-8111-111111111111", "abc", "a.b_c-D9"})
    @DisplayName("Un identificador con letras, dígitos, punto, guion y guion bajo se copia tal cual")
    void safeMessageId_shouldReturnSameValue_whenMessageIdIsSafe(String messageId) {
        assertThat(EventLogValues.safeMessageId(messageId)).isEqualTo(messageId);
    }

    @Test
    @DisplayName("El límite de 64 caracteres es exacto: 64 se copia y 65 no")
    void safeMessageId_shouldAcceptExactly64Characters_whenMessageIdIsLong() {
        assertThat(EventLogValues.safeMessageId("a".repeat(64))).isEqualTo("a".repeat(64));
        assertThat(EventLogValues.safeMessageId("a".repeat(65))).isEqualTo("invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a b", "a\nb", "a\r\nb", "a/b", "a%0Ab", "<script>"})
    @DisplayName("Un identificador vacío o con espacios, saltos de línea u otros caracteres se registra como invalid")
    void safeMessageId_shouldReturnInvalid_whenMessageIdHasUnsafeCharacters(String messageId) {
        assertThat(EventLogValues.safeMessageId(messageId)).isEqualTo("invalid");
    }
}
