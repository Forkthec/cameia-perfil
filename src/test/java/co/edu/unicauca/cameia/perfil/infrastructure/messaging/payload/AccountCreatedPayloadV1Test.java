package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La carga se lee con el ejemplo del JSON Schema publicado por Cuentas y solo con los campos que este servicio usa. */
class AccountCreatedPayloadV1Test {

    private static JsonMapper mapper;
    private static JsonNode schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        mapper = JsonMapper.builder().build();
        try (InputStream in = AccountCreatedPayloadV1Test.class.getResourceAsStream("/contracts/cuenta-creada-v1.schema.json")) {
            schema = mapper.readTree(in);
        }
    }

    @Test
    @DisplayName("El ejemplo del esquema se deserializa con la identidad y la fecha de nacimiento")
    void schemaExample_shouldDeserialize_whenReadWithPayloadRecord() {
        var example = schema.get("examples").get(0);

        var payload = mapper.treeToValue(example, AccountCreatedPayloadV1.class);

        assertThat(payload.userId()).isEqualTo("6f1d2c3b4a5e4f60718293a4b5c6d7e8");
        assertThat(payload.birthDate()).isEqualTo(LocalDate.of(2008, 3, 15));
    }

    @Test
    @DisplayName("El esquema exige los campos que este servicio lee")
    void schema_shouldRequireTheFieldsThisServiceReads() {
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(node -> required.add(node.asString()));

        assertThat(required).contains("usuarioId", "fechaNacimiento");
    }

    @Test
    @DisplayName("Una fecha con hora se rechaza en lugar de recortarse")
    void deserialize_shouldFail_whenBirthDateHasTime() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"usuarioId\":\"abc\",\"fechaNacimiento\":\"2008-03-15T00:00:00Z\"}", AccountCreatedPayloadV1.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("El correo y los campos desconocidos se ignoran")
    void deserialize_shouldIgnoreUnknownFields_whenPayloadHasEmail() {
        var payload = mapper.readValue(
                "{\"usuarioId\":\"abc\",\"email\":\"a@b.test\",\"fechaNacimiento\":\"2008-03-15\",\"rol\":\"ADMIN\"}",
                AccountCreatedPayloadV1.class);

        assertThat(payload).isEqualTo(new AccountCreatedPayloadV1("abc", LocalDate.of(2008, 3, 15)));
    }
}
