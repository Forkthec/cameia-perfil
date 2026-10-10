package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule.Rule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada regla de forma del dominio se ejecuta en el borde con el código y el mensaje del dominio. */
class DomainRuleValidatorTest {

    private static final String DATE_MESSAGE = "Ingresa una fecha válida con el formato mm/aaaa.";

    /** Un campo por regla, para validar cada una con un validador real. */
    record Probe(
            @DomainRule(Rule.COMPANY) String company,
            @DomainRule(Rule.POSITION) String position,
            @DomainRule(Rule.DESCRIPTION) String description,
            @DomainRule(Rule.INSTITUTION) String institution,
            @DomainRule(Rule.DEGREE) String degree,
            @DomainRule(Rule.EMPLOYMENT_STATUS) String employmentStatus,
            @DomainRule(Rule.EDUCATION_LEVEL) String level,
            @DomainRule(Rule.START_DATE) String startDate,
            @DomainRule(Rule.END_DATE) String endDate) {
    }

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<ConstraintViolation<Probe>> validate(String field, String value) {
        return validator.validateValue(Probe.class, field, value);
    }

    private static ErrorCode codeOf(ConstraintViolation<Probe> violation) {
        HibernateConstraintViolation<?> hibernateViolation = violation.unwrap(HibernateConstraintViolation.class);
        return hibernateViolation.getDynamicPayload(ErrorCode.class);
    }

    private static void assertRejected(String field, String value, ErrorCode code, String message) {
        var violations = validate(field, value);

        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(codeOf(violation)).isEqualTo(code);
        assertThat(violation.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("Una empresa con carácter de control se rechaza con su código y su mensaje")
    void company_shouldReject_whenTextHasControlCharacter() {
        assertRejected("company", "Acme\u0000", ErrorCode.COMPANY_INVALID_CHARACTERS,
                "La empresa tiene caracteres no permitidos.");
        assertThat(validate("company", "Acme")).isEmpty();
    }

    @Test
    @DisplayName("Un cargo con carácter de control se rechaza con su código y su mensaje")
    void position_shouldReject_whenTextHasControlCharacter() {
        assertRejected("position", "Dev\u0007", ErrorCode.POSITION_INVALID_CHARACTERS,
                "El cargo tiene caracteres no permitidos.");
        assertThat(validate("position", "Dev")).isEmpty();
    }

    @Test
    @DisplayName("Una institución con carácter de control se rechaza con su código y su mensaje")
    void institution_shouldReject_whenTextHasControlCharacter() {
        assertRejected("institution", "Uni\u007F", ErrorCode.INSTITUTION_INVALID_CHARACTERS,
                "La institución tiene caracteres no permitidos.");
        assertThat(validate("institution", "Universidad")).isEmpty();
    }

    @Test
    @DisplayName("Un título con carácter de control se rechaza con su código y su mensaje")
    void degree_shouldReject_whenTextHasControlCharacter() {
        assertRejected("degree", "Ing\u0085", ErrorCode.DEGREE_INVALID_CHARACTERS,
                "El título obtenido tiene caracteres no permitidos.");
        assertThat(validate("degree", "Ingeniería")).isEmpty();
    }

    @Test
    @DisplayName("La descripción admite saltos de línea y tabuladores, pero no otros controles")
    void description_shouldAllowLineBreaks_whenOtherControlsAreRejected() {
        assertThat(validate("description", "a\nb\tc\r")).isEmpty();
        assertRejected("description", "Hola\u0000", ErrorCode.DESCRIPTION_INVALID_CHARACTERS,
                "La descripción tiene caracteres no permitidos.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CURRENT", "ENDED", "UNKNOWN_END"})
    @DisplayName("Los estados laborales válidos pasan")
    void employmentStatus_shouldAccept_whenValueIsAnOption(String value) {
        assertThat(validate("employmentStatus", value)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"current", "FREELANCE", "Current"})
    @DisplayName("Un estado laboral que no es una opción se rechaza")
    void employmentStatus_shouldReject_whenValueIsNotAnOption(String value) {
        assertRejected("employmentStatus", value, ErrorCode.EMPLOYMENT_STATUS_INVALID_VALUE, "Selecciona una opción.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TECHNICAL", "UNDERGRADUATE", "POSTGRADUATE"})
    @DisplayName("Los niveles educativos válidos pasan")
    void educationLevel_shouldAccept_whenValueIsAnOption(String value) {
        assertThat(validate("level", value)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTORATE", "undergraduate"})
    @DisplayName("Un nivel educativo que no es una opción se rechaza")
    void educationLevel_shouldReject_whenValueIsNotAnOption(String value) {
        assertRejected("level", value, ErrorCode.EDUCATION_LEVEL_INVALID_VALUE, "Selecciona una opción.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01", "0001-01", "9999-12"})
    @DisplayName("Las fechas AAAA-MM válidas pasan")
    void dates_shouldAccept_whenFormatIsYearMonth(String value) {
        assertThat(validate("startDate", value)).isEmpty();
        assertThat(validate("endDate", value)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-13", "2024-00", "2024-1", "24-01", "13/2024", "2024", "2024-01-01", "abc", "0000-01",
            "２０２４-０１"})
    @DisplayName("Las fechas con formato inválido se rechazan con el código de su campo")
    void dates_shouldReject_whenFormatIsInvalid(String value) {
        assertRejected("startDate", value, ErrorCode.START_DATE_INVALID_FORMAT, DATE_MESSAGE);
        assertRejected("endDate", value, ErrorCode.END_DATE_INVALID_FORMAT, DATE_MESSAGE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Un valor ausente o en blanco no tiene violación: lo informa @NotBlank")
    void everyRule_shouldIgnore_whenValueIsAbsentOrBlank(String value) {
        for (var field : new String[] {"company", "position", "description", "institution", "degree",
                "employmentStatus", "level", "startDate", "endDate"}) {
            assertThat(validate(field, value)).as(field).isEmpty();
        }
    }
}
