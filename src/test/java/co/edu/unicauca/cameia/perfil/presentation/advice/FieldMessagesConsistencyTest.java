package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.model.DateBounds;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationDraft;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un código que el borde y el dominio informan lleva el mismo mensaje en las dos capas: la persona ve el mismo texto
 * sea cual sea la capa que rechazó el campo.
 */
class FieldMessagesConsistencyTest {

    private static final DateBounds BOUNDS = new DateBounds(YearMonth.of(2026, 10), null);

    @Test
    @DisplayName("El mensaje del dominio de cada código compartido es el del catálogo del borde")
    void domainMessage_shouldMatchCatalog_whenSameCodeIsRaisedByBothLayers() {
        var drafts = new WorkExperienceDraft[] {
                new WorkExperienceDraft("", "Dev", null, null, null, EmploymentStatus.CURRENT),
                new WorkExperienceDraft("a".repeat(101), "Dev", null, null, null, EmploymentStatus.CURRENT),
                new WorkExperienceDraft("ACME", "", null, null, null, EmploymentStatus.CURRENT),
                new WorkExperienceDraft("ACME", "a".repeat(101), null, null, null, EmploymentStatus.CURRENT),
                new WorkExperienceDraft("ACME", "Dev", "a".repeat(501), null, null, EmploymentStatus.CURRENT),
                new WorkExperienceDraft("ACME", "Dev", null, null, null, null)};
        var codes = java.util.EnumSet.noneOf(ErrorCode.class);

        for (var draft : drafts) {
            assertThatThrownBy(() -> WorkExperience.create(draft, BOUNDS)).isInstanceOfSatisfying(InvalidFieldsException.class,
                    e -> e.getErrors().forEach(error -> {
                        codes.add(error.code());
                        assertThat(error.message()).as(error.code().name())
                                .isEqualTo(ErrorCatalog.FIELD_MESSAGES.get(error.code()));
                    }));
        }

        assertThat(codes).containsExactlyInAnyOrder(ErrorCode.COMPANY_REQUIRED, ErrorCode.COMPANY_TOO_LONG,
                ErrorCode.POSITION_REQUIRED, ErrorCode.POSITION_TOO_LONG, ErrorCode.DESCRIPTION_TOO_LONG,
                ErrorCode.EMPLOYMENT_STATUS_REQUIRED);
    }

    @Test
    @DisplayName("El mensaje del dominio de cada código compartido de la formación es el del catálogo del borde")
    void educationMessage_shouldMatchCatalog_whenSameCodeIsRaisedByBothLayers() {
        var start = YearMonth.of(2020, 1);
        var drafts = new EducationDraft[] {
                new EducationDraft("", "Ing", EducationLevel.TECHNICAL, start, null, false),
                new EducationDraft("a".repeat(151), "Ing", EducationLevel.TECHNICAL, start, null, false),
                new EducationDraft("Unicauca", "", EducationLevel.TECHNICAL, start, null, false),
                new EducationDraft("Unicauca", "a".repeat(151), EducationLevel.TECHNICAL, start, null, false),
                new EducationDraft("Unicauca", "Ing", null, start, null, false),
                new EducationDraft("Unicauca", "Ing", EducationLevel.TECHNICAL, null, null, false)};
        var codes = java.util.EnumSet.noneOf(ErrorCode.class);

        for (var draft : drafts) {
            assertThatThrownBy(() -> Education.create(draft, BOUNDS)).isInstanceOfSatisfying(InvalidFieldsException.class,
                    e -> e.getErrors().forEach(error -> {
                        codes.add(error.code());
                        assertThat(error.message()).as(error.code().name())
                                .isEqualTo(ErrorCatalog.FIELD_MESSAGES.get(error.code()));
                    }));
        }

        assertThat(codes).containsExactlyInAnyOrder(ErrorCode.INSTITUTION_REQUIRED, ErrorCode.INSTITUTION_TOO_LONG,
                ErrorCode.DEGREE_REQUIRED, ErrorCode.DEGREE_TOO_LONG, ErrorCode.EDUCATION_LEVEL_REQUIRED,
                ErrorCode.START_DATE_REQUIRED);
    }
}
