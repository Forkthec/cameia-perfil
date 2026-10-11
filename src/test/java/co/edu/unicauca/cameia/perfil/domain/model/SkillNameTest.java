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

/** Reglas del nombre de una habilidad: recorte, NFC, obligatorio, control, largo y clave de comparación. */
class SkillNameTest {

    private static final FieldError REQUIRED =
            new FieldError("skillName", ErrorCode.SKILL_NAME_REQUIRED, "Ingresa una habilidad.");
    private static final FieldError TOO_LONG =
            new FieldError("skillName", ErrorCode.SKILL_NAME_TOO_LONG, "La habilidad no puede superar los 60 caracteres.");
    private static final String NBSP = String.valueOf((char) 160);

    static Stream<Arguments> samePairs() {
        return Stream.of(
                Arguments.of("Machine Learning", "machine learning"),
                Arguments.of("Machine Learning", "  MACHINE   LEARNING "),
                Arguments.of("Programación", "programacion"),
                Arguments.of("PROGRAMACIÓN", "programación"),
                Arguments.of("Diseño", "Diseno"),
                Arguments.of("Python", "python" + NBSP),
                Arguments.of("Ü", "u"),
                Arguments.of("R", "R "),
                Arguments.of("Go", "Gó"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("samePairs")
    @DisplayName("Dos nombres que solo difieren en mayúsculas, tildes o espacios son la misma habilidad")
    void sameAs_shouldMatch_whenNamesDifferOnlyInCaseSpacesOrAccents(String first, String second) {
        assertThat(SkillName.of(first).sameAs(SkillName.of(second))).isTrue();
    }

    @ParameterizedTest(name = "{0} ≠ {1}")
    @MethodSource("differentPairs")
    @DisplayName("Dos habilidades distintas no se confunden")
    void sameAs_shouldNotMatch_whenNamesAreDifferentSkills(String first, String second) {
        assertThat(SkillName.of(first).sameAs(SkillName.of(second))).isFalse();
    }

    static Stream<Arguments> differentPairs() {
        return Stream.of(Arguments.of("C", "C++"), Arguments.of("C#", "C"), Arguments.of("Java", "JavaScript"));
    }

    @Test
    @DisplayName("El nombre se recorta y se compone en NFC")
    void of_shouldTrimAndCompose_whenNameHasSpacesAndDecomposedLetters() {
        assertThat(SkillName.of("  Python  ").value()).isEqualTo("Python");
        assertThat(SkillName.of("Programación").value()).isEqualTo("Programaci" + (char) 243 + "n");
    }

    @Test
    @DisplayName("Se guarda con las mayúsculas y los espacios internos que escribió la persona")
    void of_shouldKeepInnerSpacesAndCase_whenStoring() {
        assertThat(SkillName.of(" Machine   Learning ").value()).isEqualTo("Machine   Learning");
        assertThat(SkillName.of("Machine   Learning").comparisonKey()).isEqualTo("machine learning");
        assertThat(SkillName.of("Programación").comparisonKey()).isEqualTo("programacion");
        assertThat(SkillName.of("Diseño").comparisonKey()).isEqualTo("diseno");
    }

    @ParameterizedTest(name = "habilidad {index}")
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("Un nombre vacío tras recortar es obligatorio")
    void of_shouldRejectRequired_whenNameIsBlank(String blank) {
        assertThatThrownBy(() -> SkillName.of(blank))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors()).containsExactly(REQUIRED));
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("limits")
    @DisplayName("El largo se mide en puntos de código: 59 y 60 se aceptan, 61 se rechaza")
    void of_shouldMeasureCodePoints_whenNameIsAtTheLimit(String unit, int repetitions, boolean accepted) {
        var text = unit.repeat(repetitions);
        if (accepted) {
            assertThat(SkillName.of(text).value()).isEqualTo(text);
        } else {
            assertThatThrownBy(() -> SkillName.of(text))
                    .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors()).containsExactly(TOO_LONG));
        }
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of("a", 59, true), Arguments.of("a", 60, true), Arguments.of("a", 61, false),
                Arguments.of("ñ", 60, true), Arguments.of("ñ", 61, false),
                Arguments.of("😀", 60, true), Arguments.of("😀", 61, false));
    }

    @ParameterizedTest(name = "carácter {index}")
    @ValueSource(strings = {"Py\0", "Py\u0007", "Py\nthon"})
    @DisplayName("Un carácter de control se rechaza con su código")
    void of_shouldRejectControlCharacter_whenNameHasOne(String text) {
        assertThatThrownBy(() -> SkillName.of(text))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        e -> assertThat(e.getErrors()).containsExactly(SkillName.CHARACTERS));
        assertThat(SkillName.CHARACTERS).isEqualTo(new FieldError(
                "skillName", ErrorCode.SKILL_NAME_INVALID_CHARACTERS, "La habilidad tiene caracteres no permitidos."));
    }

    @Test
    @DisplayName("El constructor no valida: reconstruye un nombre guardado aunque hoy no cumpliera la regla")
    void constructor_shouldNotValidate_whenRebuildingStoredValue() {
        assertThat(new SkillName("a".repeat(100)).value()).hasSize(100);
        assertThatThrownBy(() -> new SkillName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("La clave de comparación de un nombre en el límite no supera el límite")
    void comparisonKey_shouldNotExceedMaxLength_whenNameIsAtLimit() {
        assertThat(SingleLineText.length(SkillName.of("Ñ".repeat(60)).comparisonKey())).isEqualTo(60);
        assertThat(SingleLineText.length(SkillName.of("😀".repeat(60)).comparisonKey())).isEqualTo(60);
    }
}
