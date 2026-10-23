package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.RoleCatalogUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La carga del catálogo que Perfil publica coincide con el esquema publicado en {@code docs/eventos}, límite por límite. */
class RoleCatalogEventContractTest {

    private static final Path SCHEMA_FILE = Path.of("docs/eventos/perfil-catalogo-roles-actualizado-v1.schema.json");

    private static JsonMapper mapper;
    private static JsonNode schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        mapper = JsonMapper.builder().build();
        schema = mapper.readTree(Files.readString(SCHEMA_FILE));
    }

    private static JsonNode serializedExample() {
        var event = new RoleCatalogUpdated(UUID.randomUUID(), List.of(
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000001"), "Desarrollador Frontend",
                        "Frontend Developer", true),
                new ProfessionalRoleNames(UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Desarrollador Backend",
                        "Backend Developer", false)), Instant.parse("2026-10-24T12:00:00.123Z"),
                new CorrelationId("perfil-catalogo-0b9f8e7d"));
        return mapper.readTree(mapper.writeValueAsString(RoleCatalogPayloadV1.from(event)));
    }

    private static TreeSet<String> names(JsonNode object) {
        var result = new TreeSet<String>();
        object.propertyNames().forEach(result::add);
        return result;
    }

    private static TreeSet<String> required(JsonNode definition) {
        var result = new TreeSet<String>();
        definition.get("required").forEach(node -> result.add(node.asString()));
        return result;
    }

    private static void assertSameNames(JsonNode value, JsonNode definition, String where) {
        assertThat(names(value)).as("propiedades de " + where).isEqualTo(names(definition.get("properties")));
        assertThat(required(definition)).as("required de " + where).isEqualTo(names(definition.get("properties")));
        assertThat(definition.get("additionalProperties").asBoolean(true)).as("additionalProperties de " + where).isFalse();
    }

    private static JsonNode roleItems() {
        return schema.get("properties").get("roles").get("items");
    }

    @Test
    @DisplayName("Cada objeto de la carga tiene exactamente las propiedades del esquema")
    void serializedPayload_shouldHaveExactlyTheSchemaProperties_whenCatalogIsSerialized() {
        var json = serializedExample();

        assertSameNames(json, schema, "la raíz");
        assertSameNames(json.get("roles").get(0), roleItems(), "roles[]");
    }

    @Test
    @DisplayName("Los largos y las cantidades del esquema son los del dominio y los de las columnas")
    void schema_shouldMatchDomainLimits_whenLengthsAndItemsAreCompared() {
        var role = roleItems().get("properties");

        assertThat(schema.get("properties").get("roles").get("maxItems").asInt()).isEqualTo(RoleCatalogUpdated.MAX_ROLES);
        assertThat(schema.get("properties").get("roles").get("minItems").asInt()).isEqualTo(1);
        assertThat(role.get("nombreEs").get("maxLength").asInt()).isEqualTo(ProfessionalRoleNames.MAX_NAME_LENGTH);
        assertThat(role.get("nombreEn").get("maxLength").asInt()).isEqualTo(ProfessionalRoleNames.MAX_NAME_LENGTH);
        assertThat(role.get("nombreEs").get("minLength").asInt()).isEqualTo(1);
        assertThat(role.get("nombreEn").get("minLength").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Los valores del estado del esquema son los dos que la carga produce")
    void schema_shouldListEveryStatus_whenEnumIsCompared() {
        var values = new ArrayList<String>();
        roleItems().get("properties").get("estado").get("enum").forEach(node -> values.add(node.asString()));
        var produced = serializedExample().get("roles");

        assertThat(values).containsExactlyInAnyOrder("ACTIVE", "INACTIVE");
        assertThat(produced.get(0).get("estado").asString()).isIn(values);
        assertThat(produced.get(1).get("estado").asString()).isIn(values);
    }

    @Test
    @DisplayName("El ejemplo del esquema es el cuerpo válido del contrato y vuelve a serializarse igual")
    void schemaExample_shouldBeTheContractBody_whenReadWithPayloadRecord() {
        var example = schema.get("examples").get(0);

        var payload = mapper.treeToValue(example, RoleCatalogPayloadV1.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(payload))).isEqualTo(example);
        assertThat(mapper.writeValueAsString(example)).isEqualTo(RoleCatalogPayloadV1Test.VALID_CATALOG_BODY);
    }

    @Test
    @DisplayName("El instante de la carga cumple el patrón del esquema")
    void serializedPayload_shouldMatchTheInstantPattern_whenSerialized() {
        var pattern = schema.get("properties").get("actualizadoEn").get("pattern").asString();

        assertThat(serializedExample().get("actualizadoEn").asString()).matches(pattern);
        assertThat("2026-10-24T12:00:00Z").doesNotMatch(pattern);
    }
}
