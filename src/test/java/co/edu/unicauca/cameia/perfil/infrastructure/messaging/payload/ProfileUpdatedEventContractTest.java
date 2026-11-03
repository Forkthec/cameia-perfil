package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.SkillName;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.support.ActiveProfiles;
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
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La carga que Perfil publica coincide con el esquema publicado en {@code docs/eventos}, límite por límite. */
class ProfileUpdatedEventContractTest {

    private static final Path SCHEMA_FILE = Path.of("docs/eventos/perfil-profesional-actualizado-v1.schema.json");
    /** El título del rol objetivo no tiene constante pública en el dominio: su límite es 255. */
    private static final int ROLE_TITLE_MAX = 255;

    private static JsonMapper mapper;
    private static JsonNode schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        mapper = JsonMapper.builder().build();
        schema = mapper.readTree(Files.readString(SCHEMA_FILE));
    }

    private static JsonNode serializedExample() {
        var event = new ProfileUpdated(UUID.randomUUID(), ActiveProfiles.example(),
                Instant.parse("2026-10-15T12:00:00.123Z"), new CorrelationId("req-1"));
        return mapper.readTree(mapper.writeValueAsString(ProfileUpdatedPayloadV1.from(event)));
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

    private static JsonNode context() {
        return schema.get("properties").get("contextoProfesional");
    }

    private static JsonNode items(JsonNode arrayDefinition) {
        return arrayDefinition.get("items");
    }

    private static List<String> enumOf(JsonNode definition) {
        var values = new ArrayList<String>();
        definition.get("enum").forEach(node -> values.add(node.asString()));
        return values;
    }

    private static List<String> namesOf(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @Test
    @DisplayName("Cada objeto de la carga tiene exactamente las propiedades del esquema")
    void serializedPayload_shouldHaveExactlyTheSchemaProperties_whenProfileIsCompleted() {
        var json = serializedExample();
        var contextProps = context().get("properties");

        assertSameNames(json, schema, "la raíz");
        assertSameNames(json.get("contextoProfesional"), context(), "contextoProfesional");
        assertSameNames(json.get("contextoProfesional").get("experiencias").get(0), items(contextProps.get("experiencias")), "experiencias[]");
        assertSameNames(json.get("contextoProfesional").get("formaciones").get(0), items(contextProps.get("formaciones")), "formaciones[]");
        assertSameNames(json.get("contextoProfesional").get("habilidades").get(0), items(contextProps.get("habilidades")), "habilidades[]");
        assertSameNames(json.get("rolesAspirados").get(0), items(schema.get("properties").get("rolesAspirados")), "rolesAspirados[]");
    }

    @Test
    @DisplayName("La carga serializada no contiene embedding")
    void serializedPayload_shouldNotContainEmbedding_whenSerialized() {
        assertThat(mapper.writeValueAsString(serializedExample())).doesNotContain("embedding");
        assertThat(schema.toString()).doesNotContain("\"embedding\"");
    }

    @Test
    @DisplayName("Los largos y las cantidades del esquema son los del dominio")
    void schema_shouldMatchDomainLimits_whenLengthsAndItemsAreCompared() {
        var props = context().get("properties");
        var experience = items(props.get("experiencias")).get("properties");
        var education = items(props.get("formaciones")).get("properties");
        var role = items(schema.get("properties").get("rolesAspirados")).get("properties");

        assertThat(schema.get("properties").get("usuarioId").get("maxLength").asInt()).isEqualTo(FirebaseUid.MAX_LENGTH);
        assertThat(props.get("nombre").get("maxLength").asInt()).isEqualTo(ProfileName.MAX_LENGTH);
        assertThat(props.get("resumen").get("maxLength").asInt()).isEqualTo(ProfessionalSummary.MAX_LENGTH);
        assertThat(experience.get("empresa").get("maxLength").asInt()).isEqualTo(WorkExperience.MAX_COMPANY_LENGTH);
        assertThat(experience.get("cargo").get("maxLength").asInt()).isEqualTo(WorkExperience.MAX_POSITION_LENGTH);
        assertThat(experience.get("descripcion").get("maxLength").asInt()).isEqualTo(WorkExperience.MAX_DESCRIPTION_LENGTH);
        assertThat(education.get("institucion").get("maxLength").asInt()).isEqualTo(Education.MAX_INSTITUTION_LENGTH);
        assertThat(education.get("titulo").get("maxLength").asInt()).isEqualTo(Education.MAX_DEGREE_LENGTH);
        assertThat(items(props.get("habilidades")).get("properties").get("nombre").get("maxLength").asInt())
                .isEqualTo(SkillName.MAX_LENGTH);
        assertThat(role.get("nombre").get("maxLength").asInt()).isEqualTo(ROLE_TITLE_MAX);
        assertThat(props.get("experiencias").get("maxItems").asInt()).isEqualTo(ProfessionalProfile.MAX_WORK_EXPERIENCES);
        assertThat(props.get("formaciones").get("maxItems").asInt()).isEqualTo(ProfessionalProfile.MAX_EDUCATIONS);
        assertThat(props.get("habilidades").get("maxItems").asInt()).isEqualTo(ProfessionalProfile.MAX_SKILLS);
        assertThat(schema.get("properties").get("rolesAspirados").get("maxItems").asInt())
                .isEqualTo(ProfessionalProfile.MAX_TARGET_ROLES);
    }

    @Test
    @DisplayName("El inicio de la formación es texto obligatorio y el fin admite vacío")
    void schema_shouldRequireEducationStart_whenTypesAreRead() {
        var education = items(context().get("properties").get("formaciones")).get("properties");

        assertThat(education.get("inicio").get("type").asString()).isEqualTo("string");
        assertThat(education.get("fin").get("type").toString()).contains("null");
    }

    @Test
    @DisplayName("Los valores de cada enumerado del esquema son los del dominio")
    void schema_shouldListEveryDomainValue_whenEnumsAreCompared() {
        var props = context().get("properties");

        assertThat(enumOf(props.get("estado"))).containsExactlyInAnyOrderElementsOf(namesOf(ProfileStatus.values()));
        assertThat(enumOf(items(props.get("experiencias")).get("properties").get("estadoEmpleo")))
                .containsExactlyInAnyOrderElementsOf(namesOf(EmploymentStatus.values()));
        assertThat(enumOf(items(props.get("formaciones")).get("properties").get("nivel")))
                .containsExactlyInAnyOrderElementsOf(namesOf(EducationLevel.values()));
        assertThat(enumOf(items(props.get("habilidades")).get("properties").get("nivel")))
                .containsExactlyInAnyOrderElementsOf(namesOf(SkillLevel.values()));
    }

    @Test
    @DisplayName("El ejemplo del esquema se lee con la carga y vuelve a serializarse igual")
    void schemaExample_shouldDeserialize_whenReadWithPayloadRecord() {
        var example = schema.get("examples").get(0);

        var payload = mapper.treeToValue(example, ProfileUpdatedPayloadV1.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(payload))).isEqualTo(example);
    }
}
