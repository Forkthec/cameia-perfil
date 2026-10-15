package co.edu.unicauca.cameia.perfil.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El identificador de correlación solo acepta encabezados seguros; el resto cae al identificador del evento. */
class CorrelationIdTest {

    private static final UUID FALLBACK = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    @DisplayName("Un X-Request-Id de 64 caracteres permitidos se conserva tal cual")
    void fromRequestIdOrElse_shouldKeepRequestId_whenItHas64AllowedCharacters() {
        var requestId = "a".repeat(64);

        var result = CorrelationId.fromRequestIdOrElse(requestId, FALLBACK);

        assertThat(result.value()).isEqualTo(requestId);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @NullAndEmptySource
    @MethodSource("invalidRequestIds")
    @DisplayName("Un X-Request-Id ausente o no seguro se reemplaza por el identificador del evento")
    void fromRequestIdOrElse_shouldUseFallback_whenRequestIdIsInvalid(String requestId) {
        var result = CorrelationId.fromRequestIdOrElse(requestId, FALLBACK);

        assertThat(result.value()).isEqualTo("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    }

    static Stream<String> invalidRequestIds() {
        return Stream.of("a".repeat(65), "req 1", "<script>alert(1)</script>", "abc\ndef", "petición-1");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @NullSource
    @ValueSource(strings = {"a b", ""})
    @DisplayName("Construir una correlación nula o con un valor no permitido falla")
    void constructor_shouldReject_whenValueIsInvalid(String value) {
        assertThatThrownBy(() -> new CorrelationId(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
