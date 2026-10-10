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
import static org.assertj.core.groups.Tuple.tuple;

/** Reglas de la formación académica con el mes actual fijo en octubre de 2026. */
class EducationTest {

    private static final DateBounds BOUNDS = new DateBounds(YearMonth.of(2026, 10), null);
    private static final String FUTURE = "La fecha no puede ser posterior al mes actual.";

    @Test
    @DisplayName("Sin estar en curso y sin fecha de fin se acepta")
    void create_shouldAccept_whenNotInProgressWithoutEndDate() {
        var education = create("Unicauca", "Ingeniería", ym(2020, 2), null, false);

        assertThat(education.getEndDate()).isNull();
        assertThat(education.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        assertThat(education.getId()).isNotNull();
    }

    @Test
    @DisplayName("Un fin igual al inicio se acepta")
    void create_shouldAccept_whenEndEqualsStart() {
        assertThatCode(() -> create("Unicauca", "Curso", ym(2025, 2), ym(2025, 2), false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un fin un mes antes del inicio se rechaza")
    void create_shouldRejectEndBeforeStart_whenEndIsOneMonthEarlier() {
        assertRejected(() -> create("Unicauca", "Curso", ym(2025, 2), ym(2025, 1), false),
                "endDate", ErrorCode.END_DATE_BEFORE_START_DATE, "La fecha de fin no puede ser anterior a la de inicio.");
    }

    @Test
    @DisplayName("Un inicio en el mes actual se acepta")
    void create_shouldAccept_whenStartIsCurrentMonth() {
        assertThatCode(() -> create("Unicauca", "Curso", ym(2026, 10), null, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un inicio en el mes siguiente se rechaza")
    void create_shouldRejectStartInTheFuture_whenStartIsNextMonth() {
        assertRejected(() -> create("Unicauca", "Curso", ym(2026, 11), null, true),
                "startDate", ErrorCode.START_DATE_IN_THE_FUTURE, FUTURE);
    }

    @Test
    @DisplayName("Un fin en el mes siguiente se rechaza")
    void create_shouldRejectEndInTheFuture_whenEndIsNextMonth() {
        assertRejected(() -> create("Unicauca", "Curso", ym(2022, 2), ym(2026, 11), false),
                "endDate", ErrorCode.END_DATE_IN_THE_FUTURE, FUTURE);
    }

    @Test
    @DisplayName("Un fin en el mes actual se acepta")
    void create_shouldAccept_whenEndIsCurrentMonth() {
        assertThatCode(() -> create("Unicauca", "Curso", ym(2022, 2), ym(2026, 10), false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("En curso con fecha de fin se rechaza")
    void create_shouldRejectEndDateNotAllowed_whenInProgress() {
        assertRejected(() -> create("Unicauca", "Curso", ym(2025, 2), ym(2026, 9), true),
                "endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
    }

    @Test
    @DisplayName("Sin nivel ni fecha de inicio se informan como requeridos")
    void create_shouldRejectRequired_whenLevelAndStartAreMissing() {
        var draft = new EducationDraft("Unicauca", "Ing", null, null, null, false);

        assertThatThrownBy(() -> Education.create(draft, BOUNDS))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .containsExactlyInAnyOrder(
                                new FieldError("level", ErrorCode.EDUCATION_LEVEL_REQUIRED, "Elige un nivel educativo."),
                                new FieldError("startDate", ErrorCode.START_DATE_REQUIRED, "Ingresa la fecha de inicio.")));
    }

    @Test
    @DisplayName("Varias reglas rotas informan un error por campo")
    void create_shouldReportEveryField_whenSeveralRulesFail() {
        assertThatThrownBy(() -> create("", "a".repeat(151), ym(2026, 11), null, false))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactlyInAnyOrder(
                                tuple("institution", ErrorCode.INSTITUTION_REQUIRED),
                                tuple("degree", ErrorCode.DEGREE_TOO_LONG),
                                tuple("startDate", ErrorCode.START_DATE_IN_THE_FUTURE)));
    }

    @Test
    @DisplayName("Los textos de 150 puntos de código se aceptan y uno más se rechaza")
    void create_shouldCountCodePoints_whenTextIsAtLimit() {
        assertThatCode(() -> create("a".repeat(149) + "😀", "b".repeat(150), ym(2020, 1), null, false))
                .doesNotThrowAnyException();
        assertRejected(() -> create("a".repeat(149) + "😀😀", "Ing", ym(2020, 1), null, false),
                "institution", ErrorCode.INSTITUTION_TOO_LONG, "La institución no puede superar los 150 caracteres.");
    }

    @Test
    @DisplayName("Un carácter de control en el título se rechaza con su código")
    void create_shouldRejectControlCharacter_whenDegreeHasNul() {
        assertRejected(() -> create("Unicauca", "Ing\u0000", ym(2020, 1), null, false),
                "degree", ErrorCode.DEGREE_INVALID_CHARACTERS, "El título obtenido tiene caracteres no permitidos.");
    }

    @Test
    @DisplayName("Los textos se recortan antes de guardarse")
    void create_shouldTrimText_whenTextHasSpaces() {
        var education = create("  Unicauca  ", "  Ing  ", ym(2020, 1), null, false);

        assertThat(education.getInstitution()).isEqualTo("Unicauca");
        assertThat(education.getDegree()).isEqualTo("Ing");
    }

    @Test
    @DisplayName("Reconstruir una fila guardada no aplica reglas")
    void rebuild_shouldNotValidate_whenStoredTextIsLongerThanLimit() {
        var stored = new EducationDraft("a".repeat(500), "Ing", EducationLevel.TECHNICAL, ym(2020, 1), null, false);

        var education = Education.rebuild(UUID.randomUUID(), stored, DataProvenance.AI_SUGGESTED);

        assertThat(education.getInstitution()).hasSize(500);
        assertThat(education.getProvenance()).isEqualTo(DataProvenance.AI_SUGGESTED);
    }

    private static Education create(String institution, String degree, YearMonth start, YearMonth end, boolean inProgress) {
        return Education.create(new EducationDraft(institution, degree, EducationLevel.UNDERGRADUATE, start, end, inProgress), BOUNDS);
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
