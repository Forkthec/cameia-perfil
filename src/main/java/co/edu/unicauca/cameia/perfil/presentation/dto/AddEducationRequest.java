package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.SingleLineText;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.CodePointSize;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule.Rule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Cuerpo de POST /api/v1/profiles/{id}/educations; los textos llegan recortados y en NFC.
 *
 * <p>No lleva procedencia: todo lo que se agrega por esta ruta es MANUAL, y un campo {@code provenance} en el
 * cuerpo se ignora. Tampoco lleva área de estudio: el campo salió del contrato y, si llega, se ignora como
 * cualquier campo desconocido.</p>
 */
@Schema(description = "Formación académica que se agrega al perfil")
public record AddEducationRequest(
        @Schema(description = "Institución", example = "Universidad del Cauca", requiredMode = REQUIRED, minLength = 1, maxLength = 150)
        @NotBlank @CodePointSize(max = 150) @DomainRule(Rule.INSTITUTION) String institution,
        @Schema(description = "Título obtenido", example = "Ingeniería de Sistemas", requiredMode = REQUIRED, minLength = 1, maxLength = 150)
        @NotBlank @CodePointSize(max = 150) @DomainRule(Rule.DEGREE) String degree,
        @Schema(description = "Nivel educativo", example = "UNDERGRADUATE", requiredMode = REQUIRED,
                allowableValues = {"TECHNICAL", "UNDERGRADUATE", "POSTGRADUATE"})
        @NotBlank @DomainRule(Rule.EDUCATION_LEVEL) String level,
        @Schema(description = "Mes de inicio", example = "2021-01", requiredMode = REQUIRED, pattern = "^\\d{4}-(0[1-9]|1[0-2])$")
        @NotBlank @DomainRule(Rule.START_DATE) String startDate,
        @Schema(description = "Mes de fin; debe quedar vacío si la formación está en curso", example = "2025-12",
                pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true)
        @DomainRule(Rule.END_DATE) String endDate,
        @Schema(description = "En curso; ausente o null cuenta como false", example = "false", nullable = true)
        Boolean inProgress) {

    /** Normaliza antes de que corra Bean Validation, para que los límites se midan sobre lo que se guardará. */
    public AddEducationRequest {
        institution = SingleLineText.normalize(institution);
        degree = SingleLineText.normalize(degree);
        level = SingleLineText.normalize(level);
        startDate = SingleLineText.normalize(startDate);
        endDate = SingleLineText.normalizeOptional(endDate);
    }
}
