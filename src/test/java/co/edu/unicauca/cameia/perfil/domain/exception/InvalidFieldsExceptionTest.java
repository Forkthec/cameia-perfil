package co.edu.unicauca.cameia.perfil.domain.exception;

import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El acumulador junta los errores de todos los campos y conserva solo el primero de cada uno. */
class InvalidFieldsExceptionTest {

    @Test
    @DisplayName("Sin errores anotados no lanza nada")
    void throwIfAny_shouldDoNothing_whenNoErrors() {
        var collector = new InvalidFieldsException.Collector();

        assertThatCode(collector::throwIfAny).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Con dos campos rechazados lanza los dos, en el orden en que se anotaron")
    void throwIfAny_shouldThrowEveryField_whenTwoFieldsFail() {
        var collector = new InvalidFieldsException.Collector();
        collector.add("company", ErrorCode.COMPANY_TOO_LONG, "La empresa es demasiado larga.");
        collector.add("position", ErrorCode.POSITION_TOO_LONG, "El cargo es demasiado largo.");

        assertThatThrownBy(collector::throwIfAny)
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors()).containsExactly(
                        new FieldError("company", ErrorCode.COMPANY_TOO_LONG, "La empresa es demasiado larga."),
                        new FieldError("position", ErrorCode.POSITION_TOO_LONG, "El cargo es demasiado largo.")));
    }

    @Test
    @DisplayName("Si un campo falla dos veces solo se conserva el primer error")
    void add_shouldKeepFirstError_whenFieldFailsTwice() {
        var collector = new InvalidFieldsException.Collector();
        collector.add("endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        collector.add("endDate", ErrorCode.END_DATE_BEFORE_START_DATE, "La fecha de fin no puede ser anterior a la de inicio.");

        assertThatThrownBy(collector::throwIfAny)
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::code).containsExactly(ErrorCode.END_DATE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("hasError dice si el campo ya tiene un error anotado")
    void hasError_shouldReflectAnnotatedFields_whenQueried() {
        var collector = new InvalidFieldsException.Collector();
        collector.add("company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa.");

        assertThat(collector.hasError("company")).isTrue();
        assertThat(collector.hasError("position")).isFalse();
    }

    @Test
    @DisplayName("Anotar un FieldError completo equivale a anotar sus tres datos")
    void add_shouldAnnotateFieldError_whenGivenAsRecord() {
        var collector = new InvalidFieldsException.Collector();
        collector.add(new FieldError("company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa."));

        assertThatThrownBy(collector::throwIfAny)
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new FieldError("company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa.")));
    }

    @Test
    @DisplayName("of(FieldError) rechaza un solo campo")
    void of_shouldWrapSingleError_whenGivenFieldError() {
        var error = new FieldError("company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa.");

        assertThat(InvalidFieldsException.of(error).getErrors()).containsExactly(error);
    }

    @Test
    @DisplayName("La excepción no se puede construir sin errores")
    void constructor_shouldRejectEmptyList_whenNoErrors() {
        assertThatThrownBy(() -> new InvalidFieldsException(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La lista de errores que expone no se puede modificar")
    void getErrors_shouldBeUnmodifiable_whenReturned() {
        var exception = InvalidFieldsException.of("company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa.");

        assertThatThrownBy(() -> exception.getErrors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
