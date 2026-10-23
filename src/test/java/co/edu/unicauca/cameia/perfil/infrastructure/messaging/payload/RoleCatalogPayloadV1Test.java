package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La carga v1 del catálogo serializa los roles con los nombres y el orden del contrato publicado. */
class RoleCatalogPayloadV1Test {

    /** Cuerpo válido del contrato: dos roles, uno activo y uno inactivo. */
    static final String VALID_CATALOG_BODY = "{\"actualizadoEn\":\"2026-10-24T12:00:00.123Z\",\"roles\":["
            + "{\"codigo\":\"a0000001-0000-0000-0000-000000000001\",\"nombreEs\":\"Desarrollador Frontend\","
            + "\"nombreEn\":\"Frontend Developer\",\"estado\":\"ACTIVE\"},"
            + "{\"codigo\":\"a0000001-0000-0000-0000-000000000002\",\"nombreEs\":\"Desarrollador Backend\","
            + "\"nombreEn\":\"Backend Developer\",\"estado\":\"INACTIVE\"}]}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static RoleCatalogUpdated event(Instant at, ProfessionalRoleNames... roles) {
        return new RoleCatalogUpdated(UUID.randomUUID(), List.of(roles), at, new CorrelationId("perfil-catalogo-0b9f8e7d"));
    }

    @Test
    @DisplayName("El ejemplo del contrato serializa exactamente igual, campo por campo y en el mismo orden")
    void from_shouldSerializeTheContractExample_whenBuilt() {
        var payload = RoleCatalogPayloadV1.from(event(Instant.parse("2026-10-24T12:00:00.123Z"),
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000001"),
                        "Desarrollador Frontend", "Frontend Developer", true),
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000002"),
                        "Desarrollador Backend", "Backend Developer", false)));

        assertThat(JSON.writeValueAsString(payload)).isEqualTo(VALID_CATALOG_BODY);
    }

    @Test
    @DisplayName("El instante lleva siempre milisegundos y sufijo Z, aunque sean 000")
    void from_shouldKeepMillisecondsAndZ_whenInstantHasNoFraction() {
        var payload = RoleCatalogPayloadV1.from(event(Instant.parse("2026-10-24T12:00:00Z"),
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000001"), "Rol", "Role", true)));

        assertThat(payload.updatedAt()).isEqualTo("2026-10-24T12:00:00.000Z");
    }

    @Test
    @DisplayName("El nombre en inglés de respaldo y el texto con marcado viajan literales")
    void from_shouldKeepTextLiteral_whenNamesHaveMarkupOrFallback() {
        var payload = RoleCatalogPayloadV1.from(event(Instant.parse("2026-10-24T12:00:00.123Z"),
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000001"),
                        "<script>alert(1)</script> 😀", null, true)));

        var role = payload.roles().getFirst();
        assertThat(role.nameEs()).isEqualTo("<script>alert(1)</script> 😀");
        assertThat(role.nameEn()).isEqualTo("<script>alert(1)</script> 😀");
        assertThat(role.status()).isEqualTo("ACTIVE");
    }
}
