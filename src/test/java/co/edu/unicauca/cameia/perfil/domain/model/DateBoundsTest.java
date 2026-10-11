package co.edu.unicauca.cameia.perfil.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Límites de fechas por el mes de nacimiento, que se mide por meses y no por días. */
class DateBoundsTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    @Test
    @DisplayName("Sin mes de nacimiento ningún mes queda antes de la edad mínima ni del nacimiento")
    void isBefore_shouldBeFalse_whenBirthMonthIsUnknown() {
        var bounds = new DateBounds(NOW, null);

        assertThat(bounds.isBeforeMinimumAge(YearMonth.of(1900, 1))).isFalse();
        assertThat(bounds.isBeforeBirth(YearMonth.of(1900, 1))).isFalse();
    }

    @Test
    @DisplayName("El mes en que se cumplen 15 años ya no está antes de la edad mínima; el anterior sí")
    void isBeforeMinimumAge_shouldUseBirthMonthPlusFifteenYears_whenBirthMonthIsKnown() {
        var bounds = new DateBounds(NOW, YearMonth.of(2008, 3));

        assertThat(bounds.isBeforeMinimumAge(YearMonth.of(2023, 2))).isTrue();
        assertThat(bounds.isBeforeMinimumAge(YearMonth.of(2023, 3))).isFalse();
        assertThat(bounds.isBeforeMinimumAge(YearMonth.of(2023, 4))).isFalse();
    }

    @Test
    @DisplayName("Quien nace el 29 de febrero cumple 15 años en el mes de febrero, exista o no el día 29")
    void isBeforeMinimumAge_shouldUseFebruary_whenBornOnLeapDay() {
        var bounds = new DateBounds(NOW, YearMonth.of(2008, 2));

        assertThat(bounds.isBeforeMinimumAge(YearMonth.of(2023, 1))).isTrue();
        assertThat(bounds.isBeforeMinimumAge(YearMonth.of(2023, 2))).isFalse();
    }

    @Test
    @DisplayName("El mes de nacimiento no está antes del nacimiento; el anterior sí, también entre años")
    void isBeforeBirth_shouldCompareMonths_whenBirthMonthIsKnown() {
        var bounds = new DateBounds(NOW, YearMonth.of(2008, 1));

        assertThat(bounds.isBeforeBirth(YearMonth.of(2007, 12))).isTrue();
        assertThat(bounds.isBeforeBirth(YearMonth.of(2008, 1))).isFalse();
    }

    @Test
    @DisplayName("El mes actual es obligatorio")
    void constructor_shouldRejectNull_whenCurrentMonthIsMissing() {
        assertThatThrownBy(() -> new DateBounds(null, null)).isInstanceOf(NullPointerException.class);
    }
}
