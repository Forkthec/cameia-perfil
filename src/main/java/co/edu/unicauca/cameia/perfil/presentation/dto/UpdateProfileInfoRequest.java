package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.SingleLineText;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.CodePointSize;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule.Rule;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.NotEmptyIfPresent;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de PATCH /api/v1/profiles/{id}. Un campo ausente o {@code null} significa «no cambiar»; un resumen
 * en blanco significa «borrarlo» (el dominio lo rechaza en un perfil activo). Los textos llegan recortados y en NFC.
 *
 * <p>No lleva procedencia: la del resumen se deriva y un campo {@code provenance} en el cuerpo se ignora.</p>
 */
@Schema(description = "Información general del perfil; un campo ausente o null no cambia")
public record UpdateProfileInfoRequest(
        @Schema(description = "Nombre del perfil", example = "Analista de datos", minLength = 1, maxLength = 255, nullable = true)
        // @NotEmptyIfPresent rechaza el nombre que queda vacío tras recortar y deja que null signifique «sin cambio».
        @NotEmptyIfPresent @CodePointSize(max = 255) @DomainRule(Rule.PROFILE_NAME) String name,
        @Schema(description = "Resumen profesional; vacío lo borra salvo en un perfil activo",
                example = "Analista con tres años en banca.", maxLength = 2000, nullable = true)
        @CodePointSize(max = 2000) @DomainRule(Rule.SUMMARY) String summary,
        @Schema(description = "Modalidad de trabajo preferida", allowableValues = {"REMOTE", "HYBRID", "ON_SITE"}, nullable = true)
        // @NotEmptyIfPresent: una modalidad vacía no es una opción, así que se rechaza en lugar de ignorarse.
        @NotEmptyIfPresent @DomainRule(Rule.PREFERRED_MODALITY) String preferredModality) {

    /** Normaliza antes de que corra Bean Validation, para que los límites se midan sobre lo que se guardará. */
    public UpdateProfileInfoRequest {
        name = SingleLineText.normalize(name);
        summary = SingleLineText.normalize(summary);
        preferredModality = SingleLineText.normalize(preferredModality);
    }
}
