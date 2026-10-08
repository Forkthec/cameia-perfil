package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorCatalogTest {

    private static final String DTO_PACKAGE = "co.edu.unicauca.cameia.perfil.presentation.dto";

    /**
     * Códigos de campo que rechaza el dominio con {@code InvalidFieldsException}: el mensaje lo pone
     * la excepción, porque puede llevar un límite. Un código nuevo del dominio se agrega aquí.
     */
    private static final Set<ErrorCode> DOMAIN_FIELD_CODES = EnumSet.of(
            ErrorCode.PROFILE_NAME_REQUIRED, ErrorCode.PROFILE_NAME_TOO_LONG,
            ErrorCode.SUMMARY_NOT_ALLOWED, ErrorCode.SUMMARY_TOO_LONG,
            ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
            ErrorCode.PREFERRED_MODALITY_INVALID_VALUE, ErrorCode.PROVENANCE_INVALID_VALUE,
            ErrorCode.EMPLOYMENT_STATUS_INVALID_VALUE, ErrorCode.EDUCATION_LEVEL_INVALID_VALUE,
            ErrorCode.SKILL_LEVEL_INVALID_VALUE,
            ErrorCode.START_DATE_INVALID_FORMAT, ErrorCode.END_DATE_INVALID_FORMAT,
            ErrorCode.END_DATE_REQUIRED, ErrorCode.END_DATE_NOT_ALLOWED, ErrorCode.END_DATE_BEFORE_START_DATE,
            ErrorCode.COMPANY_TOO_LONG, ErrorCode.POSITION_TOO_LONG, ErrorCode.INSTITUTION_TOO_LONG,
            ErrorCode.DEGREE_TOO_LONG, ErrorCode.SKILL_NAME_TOO_LONG,
            ErrorCode.DESCRIPTION_TOO_LONG, ErrorCode.FIELD_OF_STUDY_TOO_LONG);

    @Test
    @DisplayName("Todo código es una respuesta, un campo de Bean Validation o un campo del dominio, uno solo")
    void everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded() {
        var responses = ErrorCatalog.RESPONSES.keySet();
        var fields = ErrorCatalog.FIELD_MESSAGES.keySet();
        var domainFields = DOMAIN_FIELD_CODES;
        var all = EnumSet.noneOf(ErrorCode.class);
        all.addAll(responses);
        all.addAll(fields);
        all.addAll(domainFields);

        assertThat(all).isEqualTo(EnumSet.allOf(ErrorCode.class));
        assertThat(responses).doesNotContainAnyElementsOf(fields).doesNotContainAnyElementsOf(domainFields);
        assertThat(fields).doesNotContainAnyElementsOf(domainFields);
    }

    @Test
    @DisplayName("Toda respuesta tiene estado y título")
    void everyResponse_shouldHaveStatusAndTitle_whenCatalogLoaded() {
        assertThat(ErrorCatalog.RESPONSES.values()).allSatisfy(definition -> {
            assertThat(definition.status()).isNotNull();
            assertThat(definition.title()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Un código sin respuesta en el catálogo es un error de programación")
    void of_shouldThrowIllegalState_whenCodeHasNoResponse() {
        assertThatThrownBy(() -> ErrorCatalog.of(ErrorCode.COMPANY_REQUIRED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPANY_REQUIRED");
    }

    @Test
    @DisplayName("Una restricción sin código propio usa el código y el mensaje genéricos")
    void fieldCode_shouldFallBackToGeneric_whenKeyUnknown() {
        var code = ErrorCatalog.fieldCode("Desconocido.campo.Pattern");

        assertThat(code).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ErrorCatalog.fieldMessage(code)).isEqualTo(ErrorCatalog.DEFAULT_FIELD_MESSAGE);
    }

    @Test
    @DisplayName("Una variable de ruta desconocida usa el código de valor no válido")
    void pathIdCode_shouldFallBackToInvalidValue_whenNameUnknown() {
        assertThat(ErrorCatalog.pathIdCode("otro")).isEqualTo(ErrorCode.REQUEST_INVALID_VALUE);
        assertThat(ErrorCatalog.pathIdCode("skillId")).isEqualTo(ErrorCode.SKILL_ID_INVALID_FORMAT);
    }

    @Test
    @DisplayName("Toda restricción de los DTO tiene su código de campo")
    void everyFieldConstraint_shouldHaveCode_whenDtosAreScanned() throws Exception {
        var missing = new ArrayList<String>();
        for (Class<?> dto : dtoClasses()) {
            for (Field field : dto.getDeclaredFields()) {
                for (String constraint : constraintNames(field)) {
                    var key = dto.getSimpleName() + "." + field.getName() + "." + constraint;
                    if (!ErrorCatalog.FIELD_CODES.containsKey(key)) {
                        missing.add(key);
                    }
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    private static List<String> constraintNames(Field field) {
        var names = new ArrayList<String>();
        if (field.isAnnotationPresent(NotBlank.class)) {
            names.add("NotBlank");
        }
        if (field.isAnnotationPresent(NotNull.class)) {
            names.add("NotNull");
        }
        if (field.isAnnotationPresent(Size.class)) {
            names.add("Size");
        }
        return names;
    }

    private static List<Class<?>> dtoClasses() throws Exception {
        var directory = new File(Class.forName(DTO_PACKAGE + ".ProfileResponse")
                .getProtectionDomain().getCodeSource().getLocation().toURI())
                .toPath().resolve(DTO_PACKAGE.replace('.', '/')).toFile();
        var classes = new ArrayList<Class<?>>();
        for (String name : directory.list((dir, file) -> file.endsWith(".class") && !file.contains("$"))) {
            classes.add(Class.forName(DTO_PACKAGE + "." + name.substring(0, name.length() - 6)));
        }
        return classes;
    }
}
