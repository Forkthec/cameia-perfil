package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de la experiencia laboral con el mes actual fijo en octubre de 2026. */
class WorkExperienceTest {

    private static final DateBounds BOUNDS = new DateBounds(YearMonth.of(2026, 10), null);
    private static final String FUTURE = "La fecha no puede ser posterior al mes actual.";

    @Test
    @DisplayName("Un inicio en el mes actual se acepta")
    void create_shouldAccept_whenStartIsCurrentMonth() {
        assertThatCode(() -> create("ACME", "Dev", ym(2026, 10), null, EmploymentStatus.CURRENT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un fin en el mes actual se acepta")
    void create_shouldAccept_whenEndIsCurrentMonth() {
        assertThatCode(() -> create("ACME", "Dev", ym(2025, 1), ym(2026, 10), EmploymentStatus.ENDED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un fin igual al inicio se acepta")
    void create_shouldAccept_whenEndEqualsStart() {
        assertThatCode(() -> create("ACME", "Dev", ym(2024, 5), ym(2024, 5), EmploymentStatus.ENDED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un inicio en el mes siguiente se rechaza")
    void create_shouldRejectStartInTheFuture_whenStartIsNextMonth() {
        assertRejected(() -> create("ACME", "Dev", ym(2026, 11), null, EmploymentStatus.CURRENT),
                "startDate", ErrorCode.START_DATE_IN_THE_FUTURE, FUTURE);
    }

    @Test
    @DisplayName("Un fin en el mes siguiente se rechaza")
    void create_shouldRejectEndInTheFuture_whenEndIsNextMonth() {
        assertRejected(() -> create("ACME", "Dev", null, ym(2026, 11), EmploymentStatus.ENDED),
                "endDate", ErrorCode.END_DATE_IN_THE_FUTURE, FUTURE);
    }

    @Test
    @DisplayName("Un fin un mes antes del inicio se rechaza, también entre años")
    void create_shouldRejectEndBeforeStart_whenEndIsOneMonthEarlier() {
        var message = "La fecha de fin no puede ser anterior a la de inicio.";
        assertRejected(() -> create("ACME", "Dev", ym(2024, 5), ym(2024, 4), EmploymentStatus.ENDED),
                "endDate", ErrorCode.END_DATE_BEFORE_START_DATE, message);
        assertRejected(() -> create("ACME", "Dev", ym(2025, 1), ym(2024, 12), EmploymentStatus.ENDED),
                "endDate", ErrorCode.END_DATE_BEFORE_START_DATE, message);
    }

    @Test
    @DisplayName("Terminada sin fecha de fin se rechaza")
    void create_shouldRejectEndDateRequired_whenEndedWithoutEndDate() {
        assertRejected(() -> create("ACME", "Dev", ym(2024, 5), null, EmploymentStatus.ENDED),
                "endDate", ErrorCode.END_DATE_REQUIRED, "Ingresa la fecha de fin.");
    }

    @Test
    @DisplayName("En curso o sin fin conocido con fecha de fin se rechaza")
    void create_shouldRejectEndDateNotAllowed_whenStatusIsNotEnded() {
        for (var status : new EmploymentStatus[] {EmploymentStatus.CURRENT, EmploymentStatus.UNKNOWN_END}) {
            assertRejected(() -> create("ACME", "Dev", ym(2024, 5), ym(2026, 9), status),
                    "endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        }
    }

    @Test
    @DisplayName("Sin fechas ni descripción y con fin desconocido se acepta")
    void create_shouldAcceptWithoutDates_whenUnknownEnd() {
        var experience = create("ACME", "Dev", null, null, EmploymentStatus.UNKNOWN_END);

        assertThat(experience.getStartDate()).isNull();
        assertThat(experience.getEndDate()).isNull();
        assertThat(experience.getDescription()).isNull();
        assertThat(experience.getProvenance()).isEqualTo(DataProvenance.MANUAL);
    }

    @Test
    @DisplayName("Terminada con fin y sin inicio se acepta")
    void create_shouldAcceptEndWithoutStart_whenEnded() {
        assertThatCode(() -> create("ACME", "Dev", null, ym(2023, 3), EmploymentStatus.ENDED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Varias reglas rotas informan un error por campo")
    void create_shouldReportEveryField_whenSeveralRulesFail() {
        assertThatThrownBy(() -> create("", "a".repeat(101), ym(2026, 11), null, EmploymentStatus.CURRENT))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactlyInAnyOrder(
                                org.assertj.core.groups.Tuple.tuple("company", ErrorCode.COMPANY_REQUIRED),
                                org.assertj.core.groups.Tuple.tuple("position", ErrorCode.POSITION_TOO_LONG),
                                org.assertj.core.groups.Tuple.tuple("startDate", ErrorCode.START_DATE_IN_THE_FUTURE)));
    }

    @Test
    @DisplayName("Si la fecha de fin rompe dos reglas, solo se informa la primera")
    void create_shouldReportFirstRuleOnly_whenEndDateBreaksTwo() {
        assertThatThrownBy(() -> create("ACME", "Dev", null, ym(2026, 11), EmploymentStatus.CURRENT))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::code).containsExactly(ErrorCode.END_DATE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Los textos se recortan y se cuentan en puntos de código tras pasar a NFC")
    void create_shouldNormalizeText_whenTextHasSpacesAndDecomposedLetters() {
        var trimmed = create("  Acme  ", "Dev", null, null, EmploymentStatus.CURRENT);
        var decomposed = create("é".repeat(100), "Dev", null, null, EmploymentStatus.CURRENT);

        assertThat(trimmed.getCompany()).isEqualTo("Acme");
        assertThat(decomposed.getCompany()).isEqualTo("é".repeat(100));
    }

    @Test
    @DisplayName("Una descripción en blanco se guarda como null")
    void create_shouldStoreNullDescription_whenDescriptionIsBlank() {
        var experience = WorkExperience.create(new WorkExperienceDraft("ACME", "Dev", "   ", null, null,
                EmploymentStatus.CURRENT), BOUNDS);

        assertThat(experience.getDescription()).isNull();
    }

    @Test
    @DisplayName("Reconstruir una fila guardada no aplica reglas")
    void rebuild_shouldNotValidate_whenStoredTextIsLongerThanLimit() {
        var stored = new WorkExperienceDraft("a".repeat(150), "Dev", null, null, null, EmploymentStatus.CURRENT);

        var experience = WorkExperience.rebuild(UUID.randomUUID(), stored, DataProvenance.AI_SUGGESTED);

        assertThat(experience.getCompany()).hasSize(150);
        assertThat(experience.getProvenance()).isEqualTo(DataProvenance.AI_SUGGESTED);
    }

    private static WorkExperience create(String company, String position, YearMonth start, YearMonth end,
            EmploymentStatus status) {
        return WorkExperience.create(new WorkExperienceDraft(company, position, null, start, end, status), BOUNDS);
    }

    private static YearMonth ym(int year, int month) {
        return YearMonth.of(year, month);
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field,
            ErrorCode code, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                .containsExactly(new FieldError(field, code, message)));
    }
}
