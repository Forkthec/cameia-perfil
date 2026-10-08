package co.edu.unicauca.cameia.perfil.application.command;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandValuesTest {

    @Test
    @DisplayName("Un código de la lista se convierte en su opción")
    void option_shouldReturnConstant_whenValueIsAnOption() {
        var modality = CommandValues.option(WorkModality.class, "REMOTE",
                ErrorCode.PREFERRED_MODALITY_INVALID_VALUE, "preferredModality");

        assertThat(modality).isEqualTo(WorkModality.REMOTE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"FREELANCE", "remote", ""})
    @DisplayName("Un código fuera de la lista se rechaza en su campo, sin repetir el valor ni nombres de Java")
    void option_shouldRejectField_whenValueIsNotAnOption(String value) {
        assertThatThrownBy(() -> CommandValues.option(WorkModality.class, value,
                ErrorCode.PREFERRED_MODALITY_INVALID_VALUE, "preferredModality"))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new FieldError("preferredModality",
                                ErrorCode.PREFERRED_MODALITY_INVALID_VALUE, "Selecciona una opción.")));
    }

    @Test
    @DisplayName("Una fecha AAAA-MM se convierte; sin fecha devuelve null")
    void yearMonth_shouldParse_whenFormatIsValid() {
        assertThat(CommandValues.yearMonth("2020-03", false, ErrorCode.START_DATE_INVALID_FORMAT, "startDate"))
                .isEqualTo(YearMonth.of(2020, 3));
        assertThat(CommandValues.yearMonth(null, false, ErrorCode.END_DATE_INVALID_FORMAT, "endDate")).isNull();
    }

    @Test
    @DisplayName("Solo el año se acepta cuando se permite y se lee como enero")
    void yearMonth_shouldReadJanuary_whenOnlyYearIsAllowed() {
        assertThat(CommandValues.yearMonth("2019", true, ErrorCode.START_DATE_INVALID_FORMAT, "startDate"))
                .isEqualTo(YearMonth.of(2019, 1));
        assertThat(CommandValues.yearMonth("2019-05", true, ErrorCode.START_DATE_INVALID_FORMAT, "startDate"))
                .isEqualTo(YearMonth.of(2019, 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-13", "31/02/2020", "2020"})
    @DisplayName("Una fecha que no existe o con otro formato se rechaza en su campo con el formato esperado")
    void yearMonth_shouldRejectField_whenFormatIsInvalid(String value) {
        assertThatThrownBy(() -> CommandValues.yearMonth(value, false, ErrorCode.START_DATE_INVALID_FORMAT, "startDate"))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new FieldError("startDate", ErrorCode.START_DATE_INVALID_FORMAT,
                                "Ingresa una fecha válida con el formato mm/aaaa.")));
    }
}
