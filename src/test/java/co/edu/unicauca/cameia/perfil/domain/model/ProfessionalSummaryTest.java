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

/** Reglas del resumen profesional: recorte, NFC, saltos internos, caracteres de control y largo en puntos de código. */
class ProfessionalSummaryTest {

    private static final FieldError TOO_LONG =
            new FieldError("summary", ErrorCode.SUMMARY_TOO_LONG, "El resumen no puede superar los 2000 caracteres.");

    @Test
    @DisplayName("El resumen se recorta en sus extremos y conserva los saltos de línea internos")
    void of_shouldTrimEnds_whenSummaryHasLineBreaks() {
        assertThat(ProfessionalSummary.of("\n\nTexto\n").value()).isEqualTo("Texto");
        assertThat(ProfessionalSummary.of("Línea 1\nLínea 2\tfin\r\n").value()).isEqualTo("Línea 1\nLínea 2\tfin");
        assertThat(ProfessionalSummary.of("José").value()).isEqualTo("José");
    }

    @ParameterizedTest(name = "resumen {index}")
    @ValueSource(strings = {"", "   ", "\n\n", " "})
    @DisplayName("Un resumen vacío tras recortar no es un resumen: quien llama lo convierte en null antes")
    void of_shouldThrowIllegalArgument_whenSummaryIsBlank(String blank) {
        assertThatThrownBy(() -> ProfessionalSummary.of(blank)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("limits")
    @DisplayName("El largo se mide en puntos de código: 1999 y 2000 se aceptan, 2001 se rechaza")
    void of_shouldMeasureCodePoints_whenSummaryIsAtTheLimit(String unit, int repetitions, boolean accepted) {
        var text = unit.repeat(repetitions);
        if (accepted) {
            assertThat(ProfessionalSummary.of(text).value()).isEqualTo(text);
        } else {
            assertThatThrownBy(() -> ProfessionalSummary.of(text))
                    .isInstanceOfSatisfying(InvalidFieldsException.class,
                            e -> assertThat(e.getErrors()).containsExactly(TOO_LONG));
        }
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of("a", 1999, true), Arguments.of("a", 2000, true), Arguments.of("a", 2001, false),
                Arguments.of("😀", 2000, true), Arguments.of("😀", 2001, false));
    }

    @ParameterizedTest(name = "carácter {index}")
    @ValueSource(strings = {"Hola\0", "A\u0007", "A\u0085"})
    @DisplayName("Un carácter de control distinto de tabulador, salto de línea y retorno se rechaza con su código")
    void of_shouldRejectControlCharacter_whenSummaryHasOne(String text) {
        assertThatThrownBy(() -> ProfessionalSummary.of(text))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        e -> assertThat(e.getErrors()).containsExactly(ProfessionalSummary.CHARACTERS));
        assertThat(ProfessionalSummary.CHARACTERS).isEqualTo(new FieldError(
                "summary", ErrorCode.SUMMARY_INVALID_CHARACTERS, "El resumen tiene caracteres no permitidos."));
    }

    @Test
    @DisplayName("El constructor no valida: reconstruye un resumen guardado aunque hoy no cumpliera la regla")
    void constructor_shouldNotValidate_whenRebuildingStoredValue() {
        assertThat(new ProfessionalSummary("a".repeat(2001)).value()).hasSize(2001);
        assertThatThrownBy(() -> new ProfessionalSummary(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("El texto se guarda literal: el marcado no se interpreta ni se sanea")
    void of_shouldKeepMarkupLiteral_whenSummaryHasHtml() {
        assertThat(ProfessionalSummary.of("<script>alert(1)</script>").value()).isEqualTo("<script>alert(1)</script>");
    }
}
