package co.edu.unicauca.cameia.perfil.domain.model;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkExperienceTest {

    private static final YearMonth JAN_2022 = YearMonth.of(2022, 1);
    private static final YearMonth DEC_2022 = YearMonth.of(2022, 12);

    // ── CURRENT ───────────────────────────────────────────────────────────

    @Test
    void current_acceptsNullEndDate() {
        assertThatCode(() -> exp(EmploymentStatus.CURRENT, null))
                .doesNotThrowAnyException();
    }

    @Test
    void current_throwsWhenEndDateIsProvided() {
        assertThatThrownBy(() -> exp(EmploymentStatus.CURRENT, DEC_2022))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    // ── UNKNOWN_END ───────────────────────────────────────────────────────

    @Test
    void unknownEnd_acceptsNullEndDate() {
        assertThatCode(() -> exp(EmploymentStatus.UNKNOWN_END, null))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownEnd_throwsWhenEndDateIsProvided() {
        assertThatThrownBy(() -> exp(EmploymentStatus.UNKNOWN_END, DEC_2022))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── ENDED ─────────────────────────────────────────────────────────────

    @Test
    void ended_requiresEndDate() {
        assertThatThrownBy(() -> exp(EmploymentStatus.ENDED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endDate es obligatoria");
    }

    @Test
    void ended_throwsWhenEndDateBeforeStartDate() {
        // DEC_2022 como start, JAN_2022 como end → end < start
        assertThatThrownBy(() ->
                new WorkExperience(UUID.randomUUID(), "ACME", "Dev", null,
                        DEC_2022, JAN_2022, EmploymentStatus.ENDED, DataProvenance.MANUAL)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    void ended_acceptsValidDateRange() {
        assertThatCode(() -> exp(EmploymentStatus.ENDED, DEC_2022))
                .doesNotThrowAnyException();
    }

    @Test
    void ended_acceptsSameStartAndEndDate() {
        assertThatCode(() -> exp(EmploymentStatus.ENDED, JAN_2022))
                .doesNotThrowAnyException();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static WorkExperience exp(EmploymentStatus status, YearMonth endDate) {
        return new WorkExperience(UUID.randomUUID(), "ACME Corp", "Desarrolladora", null,
                JAN_2022, endDate, status, DataProvenance.MANUAL);
    }
}
