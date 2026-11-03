package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El largo máximo se cuenta en puntos de código, no en unidades de UTF-16. */
class CodePointSizeValidatorTest {

    /** Valida un texto contra un máximo de 100, sin contexto de Bean Validation. */
    private static boolean isValid(String value) {
        var validator = new CodePointSizeValidator();
        validator.initialize(new CodePointSize() {
            @Override
            public int max() {
                return 100;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return CodePointSize.class;
            }
        });
        return validator.isValid(value, null);
    }

    @Test
    @DisplayName("Un valor ausente es válido: solo @NotBlank informa la ausencia")
    void isValid_shouldAccept_whenValueIsNull() {
        assertThat(isValid(null)).isTrue();
    }

    @Test
    @DisplayName("Exactamente el máximo es válido")
    void isValid_shouldAccept_whenLengthEqualsMax() {
        assertThat(isValid("a".repeat(100))).isTrue();
    }

    @Test
    @DisplayName("Un punto de código más que el máximo es inválido")
    void isValid_shouldReject_whenLengthExceedsMaxByOne() {
        assertThat(isValid("a".repeat(101))).isFalse();
    }

    @Test
    @DisplayName("Un emoji cuenta como un solo punto de código")
    void isValid_shouldCountEmojiAsOne_whenAtTheLimit() {
        assertThat(isValid("a".repeat(99) + "😀")).isTrue();
    }

    @Test
    @DisplayName("Un emoji después de 100 caracteres pasa el máximo")
    void isValid_shouldReject_whenEmojiExceedsTheLimit() {
        assertThat(isValid("a".repeat(100) + "😀")).isFalse();
    }
}
