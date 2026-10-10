package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.SingleLineText;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.CodePointSize;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule.Rule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Cuerpo de POST /api/v1/profiles/{id}/skills; los textos llegan recortados y en NFC.
 *
 * <p>No lleva procedencia: todo lo que se agrega por esta ruta es MANUAL, y un campo {@code provenance} en el
 * cuerpo se ignora como cualquier campo desconocido; lo que escribe la IA usa sus propias rutas.</p>
 */
@Schema(description = "Habilidad que se agrega al perfil")
public record AddSkillRequest(
        @Schema(description = "Nombre de la habilidad, texto libre", example = "Python", requiredMode = REQUIRED, minLength = 1, maxLength = 60)
        @NotBlank @CodePointSize(max = 60) @DomainRule(Rule.SKILL_NAME) String skillName,
        @Schema(description = "Nivel de la habilidad", example = "INTERMEDIATE", requiredMode = REQUIRED,
                allowableValues = {"BASIC", "INTERMEDIATE", "ADVANCED"})
        @NotBlank @DomainRule(Rule.SKILL_LEVEL) String level) {

    /** Normaliza antes de que corra Bean Validation, para que el límite se mida sobre lo que se guardará. */
    public AddSkillRequest {
        skillName = SingleLineText.normalize(skillName);
        level = SingleLineText.normalize(level);
    }
}
