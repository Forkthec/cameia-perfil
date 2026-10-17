package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileStatusInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileStatusFilterTest {

    static Stream<String> invalidValues() {
        return Stream.of("", "   ", "completed", "ARCHIVED", "COMPLETED,IN_PROGRESS", "COMPLETED ",
                "COMPLETED'--", "A".repeat(1000));
    }

    @Test
    @DisplayName("Sin parámetro no hay filtro")
    void parse_shouldReturnEmpty_whenNull() {
        assertThat(ProfileStatusFilter.parse(null)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ProfileStatus.class)
    @DisplayName("El nombre exacto de cada estado se acepta")
    void parse_shouldReturnStatus_whenExact(ProfileStatus status) {
        assertThat(ProfileStatusFilter.parse(status.name())).isEqualTo(Optional.of(status));
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    @DisplayName("Un valor vacío, en minúsculas, desconocido, repetido o con caracteres extra se rechaza")
    void parse_shouldThrow_whenInvalid(String raw) {
        assertThatThrownBy(() -> ProfileStatusFilter.parse(raw))
                .isInstanceOfSatisfying(ProfileStatusInvalidException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PROFILE_STATUS_INVALID_VALUE);
                    assertThat(e.getMessage()).isEqualTo("Selecciona una opción.");
                });
    }
}
