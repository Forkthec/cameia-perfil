package co.edu.unicauca.cameia.perfil.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("Todo código tiene forma MAYUSCULAS_CON_GUION_BAJO")
    void values_shouldMatchUpperSnakeCase_whenListed() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
                .allMatch(name -> name.matches("^[A-Z]+(_[A-Z]+)+$"));
    }

    @Test
    @DisplayName("No hay dos códigos con el mismo nombre")
    void values_shouldBeUnique_whenListed() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name)).doesNotHaveDuplicates();
    }
}
