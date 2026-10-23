package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cuerpo JSON de {@code perfil.catalogo-roles-actualizado} versión 1. Los nombres de campo los fija el contrato publicado.
 * Todo valor es texto para que el formato en el cable no dependa del serializador.
 *
 * @param updatedAt instante de la instantánea en UTC con milisegundos y sufijo {@code Z}
 * @param roles     todos los roles del catálogo, activos e inactivos
 */
public record RoleCatalogPayloadV1(
        @JsonProperty("actualizadoEn") String updatedAt,
        @JsonProperty("roles") List<RoleItem> roles) {

    private static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Construye la carga a partir del evento de dominio.
     *
     * @param event instantánea del catálogo
     * @return la carga del contrato v1
     */
    public static RoleCatalogPayloadV1 from(RoleCatalogUpdated event) {
        return new RoleCatalogPayloadV1(INSTANT_FORMAT.format(event.updatedAt()),
                event.roles().stream().map(RoleCatalogPayloadV1::itemOf).toList());
    }

    /**
     * Copia un rol; el estado viaja con el nombre del enumerado del contrato.
     *
     * @param role rol del catálogo
     * @return el elemento de {@code roles[]}
     */
    private static RoleItem itemOf(ProfessionalRoleNames role) {
        return new RoleItem(role.id().toString(), role.nameEs(), role.nameEn(), role.active() ? "ACTIVE" : "INACTIVE");
    }

    /** Un rol del catálogo (JSON {@code roles[]}); el código es el identificador del rol en Perfil. */
    public record RoleItem(@JsonProperty("codigo") String code, @JsonProperty("nombreEs") String nameEs,
            @JsonProperty("nombreEn") String nameEn, @JsonProperty("estado") String status) {
    }
}
