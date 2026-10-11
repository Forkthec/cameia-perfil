package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del nombre del perfil: recorte, NFC, obligatorio, caracteres de control y largo en puntos de código. */
class ProfileNameTest {

    private static final FieldError REQUIRED =
            new FieldError("name", ErrorCode.PROFILE_NAME_REQUIRED, "Ingresa un nombre para el perfil.");
    private static final FieldError TOO_LONG =
            new FieldError("name", ErrorCode.PROFILE_NAME_TOO_LONG, "El nombre no puede superar los 255 caracteres.");

    @Test
    @DisplayName("El nombre se recorta, se compone en NFC y conserva los espacios internos")
    void of_shouldTrimAndCompose_whenNameHasSpacesAndDecomposedLetters() {
        assertThat(ProfileName.of("  Analista  de datos  ").value()).isEqualTo("Analista  de datos");
        assertThat(ProfileName.of("José").value()).isEqualTo("José");
    }

    @ParameterizedTest(name = "nombre {index}")
    @ValueSource(strings = {"", "   ", "\t\n", " ", "﻿"})
    @DisplayName("Un nombre vacío tras recortar es obligatorio")
    void of_shouldRejectRequired_whenNameIsBlank(String blank) {
        assertThatThrownBy(() -> ProfileName.of(blank))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        e -> assertThat(e.getErrors()).containsExactly(REQUIRED));
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("limits")
    @DisplayName("El largo se mide en puntos de código: 254 y 255 se aceptan, 256 se rechaza")
    void of_shouldMeasureCodePoints_whenNameIsAtTheLimit(String unit, int repetitions, boolean accepted) {
        var text = unit.repeat(repetitions);
        if (accepted) {
            assertThat(ProfileName.of(text).value()).isEqualTo(text);
        } else {
            assertThatThrownBy(() -> ProfileName.of(text))
                    .isInstanceOfSatisfying(InvalidFieldsException.class,
                            e -> assertThat(e.getErrors()).containsExactly(TOO_LONG));
        }
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of("a", 254, true), Arguments.of("a", 255, true), Arguments.of("a", 256, false),
                Arguments.of("ñ", 255, true), Arguments.of("ñ", 256, false),
                Arguments.of("😀", 255, true), Arguments.of("😀", 256, false));
    }

    @ParameterizedTest(name = "carácter {index}")
    @ValueSource(strings = {"Analista\0", "A\u0007", "A\nB", "A\tB", "A\u0085"})
    @DisplayName("Un carácter de control, incluido el salto de línea, se rechaza con su código")
    void of_shouldRejectControlCharacter_whenNameHasOne(String text) {
        assertThatThrownBy(() -> ProfileName.of(text))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        e -> assertThat(e.getErrors()).containsExactly(ProfileName.CHARACTERS));
        assertThat(ProfileName.CHARACTERS).isEqualTo(new FieldError(
                "name", ErrorCode.PROFILE_NAME_INVALID_CHARACTERS, "El nombre tiene caracteres no permitidos."));
    }

    @Test
    @DisplayName("El constructor no valida: reconstruye un nombre guardado aunque hoy no cumpliera la regla")
    void constructor_shouldNotValidate_whenRebuildingStoredValue() {
        assertThat(new ProfileName("a".repeat(256)).value()).hasSize(256);
        assertThatThrownBy(() -> new ProfileName(null)).isInstanceOf(NullPointerException.class);
    }
}
