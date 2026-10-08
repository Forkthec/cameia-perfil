package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de obligatorio, largo, signo y fechas de los objetos de valor y entidades del perfil. */
class ProfileValueRulesTest {

    private static final YearMonth START = YearMonth.of(2020, 1);

    static Stream<Arguments> violations() {
        return Stream.of(
                Arguments.of("nombre en blanco", (Executable) () -> new ProfileName(" "),
                        "name", ErrorCode.PROFILE_NAME_REQUIRED, "Ingresa un nombre para el perfil."),
                Arguments.of("nombre de 256", (Executable) () -> new ProfileName("a".repeat(256)),
                        "name", ErrorCode.PROFILE_NAME_TOO_LONG, "El nombre no puede superar los 255 caracteres."),
                Arguments.of("resumen en blanco", (Executable) () -> new ProfessionalSummary("\t"),
                        "summary", ErrorCode.SUMMARY_REQUIRED, "Ingresa el resumen profesional."),
                Arguments.of("resumen de 2001", (Executable) () -> new ProfessionalSummary("a".repeat(2001)),
                        "summary", ErrorCode.SUMMARY_TOO_LONG, "El resumen no puede superar los 2000 caracteres."),
                Arguments.of("salario negativo", (Executable) () -> new SalaryExpectation(new BigDecimal("-1")),
                        "amount", ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
                        "La expectativa salarial no puede ser negativa."),
                Arguments.of("salario de 14 dígitos", (Executable) () -> new SalaryExpectation(new BigDecimal("10000000000000")),
                        "amount", ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
                        "La expectativa salarial no puede tener más de 13 dígitos."),
                Arguments.of("salario en notación científica de 14 dígitos",
                        (Executable) () -> new SalaryExpectation(new BigDecimal("1E+13")),
                        "amount", ErrorCode.SALARY_EXPECTATION_OUT_OF_RANGE,
                        "La expectativa salarial no puede tener más de 13 dígitos."),
                Arguments.of("habilidad en blanco", (Executable) () -> skill(" "),
                        "skillName", ErrorCode.SKILL_NAME_REQUIRED, "Ingresa una habilidad."),
                Arguments.of("habilidad de 256", (Executable) () -> skill("a".repeat(256)),
                        "skillName", ErrorCode.SKILL_NAME_TOO_LONG, "La habilidad no puede superar los 255 caracteres."),
                Arguments.of("empresa en blanco", (Executable) () -> experience(" ", "Dev"),
                        "company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa."),
                Arguments.of("empresa de 501", (Executable) () -> experience("a".repeat(501), "Dev"),
                        "company", ErrorCode.COMPANY_TOO_LONG, "La empresa no puede superar los 500 caracteres."),
                Arguments.of("descripción de 2001",
                        (Executable) () -> new WorkExperience(UUID.randomUUID(), "ACME", "Dev", "a".repeat(2001), START,
                                null, EmploymentStatus.CURRENT, DataProvenance.MANUAL),
                        "description", ErrorCode.DESCRIPTION_TOO_LONG,
                        "La descripción no puede superar los 2000 caracteres."),
                Arguments.of("cargo en blanco", (Executable) () -> experience("ACME", ""),
                        "position", ErrorCode.POSITION_REQUIRED, "Ingresa el cargo."),
                Arguments.of("cargo de 501", (Executable) () -> experience("ACME", "a".repeat(501)),
                        "position", ErrorCode.POSITION_TOO_LONG, "El cargo no puede superar los 500 caracteres."),
                Arguments.of("institución en blanco", (Executable) () -> education(" ", "Ing", null, false),
                        "institution", ErrorCode.INSTITUTION_REQUIRED, "Ingresa la institución."),
                Arguments.of("institución de 501", (Executable) () -> education("a".repeat(501), "Ing", null, false),
                        "institution", ErrorCode.INSTITUTION_TOO_LONG,
                        "La institución no puede superar los 500 caracteres."),
                Arguments.of("título en blanco", (Executable) () -> education("Unicauca", " ", null, false),
                        "degree", ErrorCode.DEGREE_REQUIRED, "Ingresa el título obtenido."),
                Arguments.of("título de 501", (Executable) () -> education("Unicauca", "a".repeat(501), null, false),
                        "degree", ErrorCode.DEGREE_TOO_LONG, "El título obtenido no puede superar los 500 caracteres."),
                Arguments.of("área de estudio de 501",
                        (Executable) () -> new Education(UUID.randomUUID(), "Unicauca", "Ing", "a".repeat(501),
                                EducationLevel.UNDERGRADUATE, START, null, false, DataProvenance.MANUAL),
                        "fieldOfStudy", ErrorCode.FIELD_OF_STUDY_TOO_LONG,
                        "El área de estudio no puede superar los 500 caracteres."),
                Arguments.of("formación terminada antes de empezar",
                        (Executable) () -> education("Unicauca", "Ing", START.minusMonths(1), false),
                        "endDate", ErrorCode.END_DATE_BEFORE_START_DATE,
                        "La fecha de fin no puede ser anterior a la de inicio."),
                Arguments.of("formación en curso con fecha de fin",
                        (Executable) () -> education("Unicauca", "Ing", START.plusYears(1), true),
                        "endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("violations")
    @DisplayName("Cada regla incumplida rechaza su campo con su código y un mensaje que dice qué corregir")
    void constructor_shouldRejectField_whenRuleIsBroken(
            String caseName, Executable build, String field, ErrorCode code, String message) {
        assertThatThrownBy(build::execute)
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getErrors()).containsExactly(new FieldError(field, code, message));
                });
    }

    @Test
    @DisplayName("Los valores en el límite de cada regla se aceptan")
    void constructor_shouldAccept_whenValuesAreAtTheLimit() {
        assertThatCode(() -> {
            new ProfileName("a".repeat(255));
            new ProfessionalSummary("a".repeat(2000));
            new SalaryExpectation(BigDecimal.ZERO);
            new SalaryExpectation(new BigDecimal("9999999999999.99"));
            new SalaryExpectation(new BigDecimal("1E+12"));
            skill("a".repeat(255));
            experience("a".repeat(500), "a".repeat(500));
            new WorkExperience(UUID.randomUUID(), "ACME", "Dev", "a".repeat(2000), START, null,
                    EmploymentStatus.CURRENT, DataProvenance.MANUAL);
            new Education(UUID.randomUUID(), "Unicauca", "Ing", "a".repeat(500), EducationLevel.UNDERGRADUATE,
                    START, START, false, DataProvenance.MANUAL);
            education("a".repeat(500), "a".repeat(500), START.plusYears(1), false);
            education("Unicauca", "Ing", null, true);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sin campos rechazados no se puede crear la excepción")
    void invalidFields_shouldRequireAtLeastOneError_whenCreated() {
        assertThatThrownBy(() -> new InvalidFieldsException(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ProfileSkill skill(String name) {
        return new ProfileSkill(UUID.randomUUID(), name, SkillLevel.BASIC, DataProvenance.MANUAL);
    }

    private static WorkExperience experience(String company, String position) {
        return new WorkExperience(UUID.randomUUID(), company, position, null, START, null,
                EmploymentStatus.CURRENT, DataProvenance.MANUAL);
    }

    private static Education education(String institution, String degree, YearMonth end, boolean inProgress) {
        return new Education(UUID.randomUUID(), institution, degree, null, EducationLevel.UNDERGRADUATE,
                START, end, inProgress, DataProvenance.MANUAL);
    }
}
