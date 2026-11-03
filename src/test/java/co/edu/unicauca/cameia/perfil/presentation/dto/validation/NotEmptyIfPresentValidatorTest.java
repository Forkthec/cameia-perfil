package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Un texto enviado no puede quedar vacío; uno no enviado se deja pasar. */
class NotEmptyIfPresentValidatorTest {

    private final NotEmptyIfPresentValidator validator = new NotEmptyIfPresentValidator();

    @Test
    @DisplayName("Un texto ausente (null) es válido: significa «no cambiar»")
    void isValid_shouldAccept_whenValueIsNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("Un texto vacío es inválido")
    void isValid_shouldReject_whenValueIsEmpty() {
        assertThat(validator.isValid("", null)).isFalse();
    }

    @Test
    @DisplayName("Un texto con al menos un carácter es válido")
    void isValid_shouldAccept_whenValueHasOneCharacter() {
        assertThat(validator.isValid("a", null)).isTrue();
        assertThat(validator.isValid("😀", null)).isTrue();
    }
}
