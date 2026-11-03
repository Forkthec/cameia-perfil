package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.SingleLineText;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.CodePointSize;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule.Rule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Cuerpo de POST /api/v1/profiles/{id}/work-experiences; los textos llegan recortados y en NFC.
 *
 * <p>No lleva procedencia: todo lo que se agrega por esta ruta es MANUAL, y un campo {@code provenance} en el
 * cuerpo se ignora. Lo que escribe la IA usa sus propias rutas.</p>
 */
@Schema(description = "Experiencia laboral que se agrega al perfil")
public record AddWorkExperienceRequest(
        @Schema(description = "Empresa", example = "Bancolombia", requiredMode = REQUIRED, minLength = 1, maxLength = 100)
        @NotBlank @CodePointSize(max = 100) @DomainRule(Rule.COMPANY) String company,
        @Schema(description = "Cargo", example = "Desarrolladora Backend", requiredMode = REQUIRED, minLength = 1, maxLength = 100)
        @NotBlank @CodePointSize(max = 100) @DomainRule(Rule.POSITION) String position,
        @Schema(description = "Descripción opcional; vacía se guarda como null", example = "APIs REST con Spring Boot",
                maxLength = 500, nullable = true)
        @CodePointSize(max = 500) @DomainRule(Rule.DESCRIPTION) String description,
        @Schema(description = "Mes de inicio, opcional", example = "2024-06", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true)
        @DomainRule(Rule.START_DATE) String startDate,
        @Schema(description = "Mes de fin; obligatorio solo con ENDED", example = "2024-12",
                pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true)
        @DomainRule(Rule.END_DATE) String endDate,
        @Schema(description = "Estado de la experiencia", example = "ENDED", requiredMode = REQUIRED,
                allowableValues = {"CURRENT", "ENDED", "UNKNOWN_END"})
        @NotBlank @DomainRule(Rule.EMPLOYMENT_STATUS) String employmentStatus) {

    /** Normaliza antes de que corra Bean Validation, para que los límites se midan sobre lo que se guardará. */
    public AddWorkExperienceRequest {
        company = SingleLineText.normalize(company);
        position = SingleLineText.normalize(position);
        description = SingleLineText.normalizeOptional(description);
        startDate = SingleLineText.normalizeOptional(startDate);
        endDate = SingleLineText.normalizeOptional(endDate);
        employmentStatus = SingleLineText.normalize(employmentStatus);
    }
}
