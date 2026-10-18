package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.ProfileSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/v1/profiles}: los perfiles del usuario en un objeto con la lista dentro.
 *
 * <p>Es una vista resumida: no lleva resumen profesional, experiencia, formación ni habilidades. Los nombres de
 * los tipos anidados son largos a propósito: springdoc nombra los esquemas por el nombre simple de la clase y
 * «Item» o «Role» chocarían con los de otros DTO.</p>
 *
 * @param profiles perfiles del primero creado al último; vacía si el usuario no tiene ninguno
 */
@Schema(description = "Perfiles del usuario, del primero creado al último")
public record ProfileListResponse(
        @Schema(description = "Perfiles; vacía si el usuario no tiene ninguno. Como máximo 5 por el plan")
        List<ProfileListItem> profiles) {

    /**
     * Perfil en la lista.
     *
     * @param id          identificador del perfil
     * @param name        nombre del perfil; {@code null} si aún no tiene
     * @param status      estado del perfil
     * @param targetRoles roles objetivo en el orden en que se agregaron
     */
    @Schema(description = "Perfil en la lista")
    public record ProfileListItem(
            @Schema(description = "Identificador del perfil", format = "uuid",
                    example = "3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10")
            UUID id,
            @Schema(description = "Nombre del perfil; null si aún no tiene", example = "Analista de datos",
                    nullable = true)
            String name,
            @Schema(description = "Estado del perfil. Todo estado distinto de COMPLETED se muestra como borrador",
                    allowableValues = {"PENDING", "IN_PROGRESS", "IN_REVIEW", "COMPLETED"}, example = "COMPLETED")
            String status,
            @Schema(description = "Roles objetivo en el orden en que se agregaron; vacía si no tiene")
            List<ProfileListTargetRole> targetRoles) { }

    /**
     * Rol objetivo de un perfil en la lista.
     *
     * @param professionalRoleId identificador del rol en el catálogo
     * @param roleTitle          nombre del rol guardado en el perfil
     */
    @Schema(description = "Rol objetivo del perfil")
    public record ProfileListTargetRole(
            @Schema(description = "Rol del catálogo", format = "uuid",
                    example = "a0000002-0000-0000-0000-000000000002")
            UUID professionalRoleId,
            @Schema(description = "Nombre del rol guardado en el perfil", example = "Analista de Datos")
            String roleTitle) { }

    /**
     * Arma la respuesta desde la vista del dominio.
     *
     * @param summaries perfiles ya ordenados
     * @return la respuesta con la lista dentro
     */
    public static ProfileListResponse from(List<ProfileSummary> summaries) {
        return new ProfileListResponse(summaries.stream()
                .map(s -> new ProfileListItem(s.id().value(), s.name(), s.status().name(),
                        s.targetRoles().stream()
                                .map(r -> new ProfileListTargetRole(r.professionalRoleId(), r.roleTitle()))
                                .toList()))
                .toList());
    }
}
