package co.edu.unicauca.cameia.perfil.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Normalización, largo y caracteres de control del texto de una línea. Los invisibles se escriben con escapes. */
class SingleLineTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "'  Acme  '|Acme",
            "'\u00A0Acme\u00A0'|Acme",
            "'\uFEFFAcme'|Acme",
            "'\t\nAcme\r\n'|Acme"})
    @DisplayName("Quita los espacios de los extremos que quita JavaScript")
    void normalize_shouldTrimJavaScriptSpaces_whenTextHasThem(String raw, String expected) {
        assertThat(SingleLineText.normalize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Conserva los espacios internos")
    void normalize_shouldKeepInnerSpaces_whenTextHasThem() {
        assertThat(SingleLineText.normalize("Acme  S.A.")).isEqualTo("Acme  S.A.");
    }

    @Test
    @DisplayName("El espacio de ancho cero no es espacio para JavaScript y se conserva")
    void normalize_shouldKeepZeroWidthSpace_whenAtTheEdge() {
        assertThat(SingleLineText.normalize("\u200BAcme")).isEqualTo("\u200BAcme");
    }

    @Test
    @DisplayName("Compone el texto descompuesto a NFC y cuenta un solo punto de código")
    void normalize_shouldComposeToNfc_whenTextIsDecomposed() {
        String normalized = SingleLineText.normalize("e\u0301");

        assertThat(normalized).isEqualTo("\u00E9");
        assertThat(SingleLineText.length(normalized)).isEqualTo(1);
    }

    @Test
    @DisplayName("Un texto ausente sigue ausente")
    void normalize_shouldReturnNull_whenTextIsNull() {
        assertThat(SingleLineText.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", " ", "", "\t\n", "\u00A0"})
    @DisplayName("Un opcional que queda vacío pasa a null")
    void normalizeOptional_shouldReturnNull_whenOnlySpacesRemain(String raw) {
        assertThat(SingleLineText.normalizeOptional(raw)).isNull();
    }

    @Test
    @DisplayName("Un opcional con texto se recorta")
    void normalizeOptional_shouldTrim_whenTextRemains() {
        assertThat(SingleLineText.normalizeOptional("  Dev ")).isEqualTo("Dev");
    }

    @Test
    @DisplayName("Un opcional ausente sigue ausente")
    void normalizeOptional_shouldReturnNull_whenTextIsNull() {
        assertThat(SingleLineText.normalizeOptional(null)).isNull();
    }

    @Test
    @DisplayName("Cuenta puntos de código, no unidades de UTF-16")
    void length_shouldCountCodePoints_whenTextHasEmoji() {
        assertThat(SingleLineText.length("😀")).isEqualTo(1);
        assertThat(SingleLineText.length("a😀b")).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Acme\u0000", "Acme\u0007", "A\u007Fcme", "A\u0085"})
    @DisplayName("Detecta caracteres de control en los dos modos")
    void hasControlCharacter_shouldDetectForbiddenCharacters_whenTextHasThem(String text) {
        assertThat(SingleLineText.hasControlCharacter(text, false)).isTrue();
        assertThat(SingleLineText.hasControlCharacter(text, true)).isTrue();
    }

    @Test
    @DisplayName("Tabulador, salto de línea y retorno solo se admiten en campos de varias líneas")
    void hasControlCharacter_shouldAllowLineBreaks_whenFieldIsMultiline() {
        assertThat(SingleLineText.hasControlCharacter("a\nb\tc\r", false)).isTrue();
        assertThat(SingleLineText.hasControlCharacter("a\nb\tc\r", true)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Acme", "Ñandú", "👨\u200D👩\u200D👧"})
    @DisplayName("Los textos normales y los emojis compuestos no tienen caracteres de control")
    void hasControlCharacter_shouldBeFalse_whenTextIsPlain(String text) {
        assertThat(SingleLineText.hasControlCharacter(text, false)).isFalse();
    }
}
