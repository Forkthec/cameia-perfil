package co.edu.unicauca.cameia.perfil.presentation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el documento OpenAPI se genera con el servidor real y publica la forma común
 * de error con sus campos y los códigos de las respuestas de error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OpenApiDocumentIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Test
    @DisplayName("El OpenAPI publica el error común y sus códigos, y no publica los endpoints sin HU del MVP")
    void apiDocs_shouldDescribeCommonErrorAndCodes_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"ApiError\"", "\"ApiFieldError\"", "\"requestId\"", "\"missingRequirements\"")
                .contains("IDENTITY_REQUIRED", "PROFILE_ID_INVALID_FORMAT", "PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE")
                .contains("WORK_EXPERIENCE_NOT_FOUND", "TARGET_ROLE_NOT_FOUND", "END_DATE_BEFORE_START_DATE")
                // Fecha de nacimiento: el 503 y las reglas de los 15 años y del nacimiento están en el contrato.
                .contains("BIRTH_DATE_UNAVAILABLE", "START_DATE_BEFORE_MINIMUM_AGE", "END_DATE_BEFORE_MINIMUM_AGE",
                        "START_DATE_BEFORE_BIRTH")
                // Sin HU en el MVP (hallazgo I-024): no se publica para que Frontend no lo integre.
                .doesNotContain("/review-requests", "/salary-expectation");
    }

    @Test
    @DisplayName("El OpenAPI describe la lista de perfiles: respuestas 200, 401, 406 y 422, el filtro y el esquema")
    void apiDocs_shouldDescribeListProfiles_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        var operation = "$.paths['/api/v1/profiles'].get";
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, operation + ".responses").keySet())
                .contains("200", "401", "406", "422");
        assertThat(JsonPath.<String>read(body, operation + ".responses.200.headers['Cache-Control'].description"))
                .isNotBlank();
        assertThat(JsonPath.<java.util.List<String>>read(body, operation + ".parameters[?(@.name=='status')].description"))
                .singleElement().asString().contains("COMPLETED");
        assertThat(JsonPath.<java.util.List<Object>>read(body, operation + ".parameters[?(@.name=='X-User-Id')]"))
                .as("la identidad la pone el Gateway: no se publica como parámetro").isEmpty();
        assertThat(JsonPath.<java.util.List<Object>>read(body, operation + ".responses.422.content.*").get(0).toString())
                .contains("PROFILE_STATUS_INVALID_VALUE", "Selecciona una opción.");
        var schema = "$.components.schemas.ProfileListResponse";
        assertThat(JsonPath.<String>read(body, schema + ".properties.profiles.items.$ref")).endsWith("ProfileListItem");
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.components.schemas.ProfileListItem.properties").keySet())
                .containsExactlyInAnyOrder("id", "name", "status", "targetRoles");
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.components.schemas.ProfileListTargetRole.properties").keySet())
                .containsExactlyInAnyOrder("professionalRoleId", "roleTitle");
    }

    @Test
    @DisplayName("El OpenAPI documenta el 422 de consulta mal codificada en el catálogo de roles y en la lista de perfiles")
    void apiDocs_shouldDocumentQueryInvalidFormat_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        for (var path : java.util.List.of("/api/v1/profiles/professional-roles", "/api/v1/profiles")) {
            assertThat(JsonPath.<String>read(body, "$.paths['" + path + "'].get.responses.422.description"))
                    .as(path).contains("REQUEST_QUERY_INVALID_FORMAT");
        }
    }

    @Test
    @DisplayName("Las 15 operaciones publicadas documentan el 406, el 415 (con multipart) y el 413 de protocolo")
    void apiDocs_shouldDocumentProtocolErrorsInEveryOperation_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        var operations = JsonPath.<java.util.List<java.util.Map<String, Object>>>read(body, "$.paths.*.*");
        assertThat(operations).as("operaciones publicadas (la expectativa salarial no se publica)").hasSize(15);
        for (var operation : operations) {
            @SuppressWarnings("unchecked")
            var responses = (java.util.Map<String, java.util.Map<String, Object>>) operation.get("responses");
            assertThat(responses.get("406").get("description").toString()).contains("MEDIA_TYPE_NOT_ACCEPTABLE");
            assertThat(responses.get("415").get("description").toString())
                    .contains("MEDIA_TYPE_NOT_ALLOWED", "REQUEST_MULTIPART_INVALID_FORMAT");
            assertThat(responses.get("413").get("description").toString())
                    .contains("REQUEST_BODY_TOO_LARGE", "1 MiB por parte", "10 MiB por petición");
        }
    }

    @Test
    @DisplayName("La ruta /error del contenedor no aparece en el OpenAPI")
    void apiDocs_shouldNotListErrorRoute_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.paths").keySet()).doesNotContain("/error");
    }

    @Test
    @DisplayName("El PATCH del rol objetivo describe su campo igual que el POST: uuid, ejemplo y obligatorio")
    void apiDocs_shouldDescribeUpdateTargetRoleBody_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        var schema = "$.components.schemas.UpdateTargetRoleRequest";
        assertThat(JsonPath.<String>read(body, schema + ".properties.professionalRoleId.format")).isEqualTo("uuid");
        assertThat(JsonPath.<String>read(body, schema + ".properties.professionalRoleId.description")).isNotBlank();
        assertThat(JsonPath.<String>read(body, schema + ".properties.professionalRoleId.example"))
                .isEqualTo("a0000001-0000-0000-0000-000000000003");
        assertThat(JsonPath.<java.util.List<String>>read(body, schema + ".required")).containsExactly("professionalRoleId");
    }

    @Test
    @DisplayName("El DELETE del rol objetivo publica un ejemplo con code por cada respuesta de error y avisa que ignora el cuerpo")
    void apiDocs_shouldDescribeDeleteTargetRoleErrorExamples_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        var operation = "$.paths['/api/v1/profiles/{id}/target-roles/{roleId}'].delete";
        assertThat(JsonPath.<String>read(body, operation + ".description")).contains("cuerpo");
        for (var status : java.util.List.of("401", "403", "404", "409", "422")) {
            // Un ejemplo se publica como «example»; varios, como «examples».
            var content = JsonPath.<java.util.List<java.util.Map<String, Object>>>read(
                    body, operation + ".responses." + status + ".content.*").get(0);
            assertThat(content.containsKey("example") || content.containsKey("examples"))
                    .as("ejemplo de la respuesta " + status).isTrue();
            assertThat(content.toString()).as("respuesta " + status).contains("code=", "requestId=");
        }
        assertThat(JsonPath.<java.util.List<Object>>read(body, operation + ".responses.409.content.*").get(0).toString())
                .contains("TARGET_ROLE_NOT_ALLOWED", "PROFILE_UPDATE_IN_PROGRESS");
    }

    @Test
    @DisplayName("El rol del catálogo y el rol objetivo del perfil describen cada campo, y la procedencia lista sus tres valores")
    void apiDocs_shouldDescribeRoleFields_whenRequested() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

        for (var field : java.util.List.of("id", "nombre", "categoria")) {
            var property = "$.components.schemas.ProfessionalRoleResponse.properties." + field;
            assertThat(JsonPath.<String>read(body, property + ".description")).isNotBlank();
            assertThat(JsonPath.<Object>read(body, property + ".example")).isNotNull();
        }
        for (var field : java.util.List.of("id", "professionalRoleId", "roleTitle", "provenance")) {
            var property = "$.components.schemas.TargetRoleItem.properties." + field;
            assertThat(JsonPath.<String>read(body, property + ".description")).isNotBlank();
            assertThat(JsonPath.<Object>read(body, property + ".example")).isNotNull();
        }
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.components.schemas.TargetRoleItem.properties.provenance.enum"))
                .containsExactly("MANUAL", "AI_SUGGESTED", "AI_EDITED");
    }

    @Test
    @DisplayName("El OpenAPI de la experiencia publica los límites de 100, 100 y 500, el formato de las fechas y las opciones del estado")
    void addWorkExperienceRequest_shouldDocumentLimits_whenApiDocsAreGenerated() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        var schema = JsonMapper.builder().build().readTree(response.body())
                .get("components").get("schemas").get("AddWorkExperienceRequest").get("properties");
        assertThat(schema.get("company").get("maxLength").asInt()).isEqualTo(100);
        assertThat(schema.get("position").get("maxLength").asInt()).isEqualTo(100);
        assertThat(schema.get("description").get("maxLength").asInt()).isEqualTo(500);
        assertThat(schema.get("startDate").get("pattern").asString()).isEqualTo("^\\d{4}-(0[1-9]|1[0-2])$");
        assertThat(schema.get("endDate").get("pattern").asString()).isEqualTo("^\\d{4}-(0[1-9]|1[0-2])$");
        assertThat(schema.get("employmentStatus").get("enum").valueStream().map(v -> v.asString()).toList())
                .containsExactlyInAnyOrder("CURRENT", "ENDED", "UNKNOWN_END");
        assertThat(schema.has("provenance")).isFalse();
    }

    @Test
    @DisplayName("El OpenAPI de la información general publica los límites de 255 y 2000, la modalidad, sin procedencia y con la procedencia del resumen")
    void updateProfileInfoRequest_shouldDocumentLimits_whenApiDocsAreGenerated() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        var schemas = JsonMapper.builder().build().readTree(response.body()).get("components").get("schemas");
        var schema = schemas.get("UpdateProfileInfoRequest").get("properties");
        assertThat(schema.get("name").get("maxLength").asInt()).isEqualTo(255);
        assertThat(schema.get("name").get("minLength").asInt()).isEqualTo(1);
        assertThat(schema.get("summary").get("maxLength").asInt()).isEqualTo(2000);
        assertThat(schema.get("preferredModality").get("enum").valueStream().map(v -> v.asString()).toList())
                .containsExactlyInAnyOrder("REMOTE", "HYBRID", "ON_SITE");
        assertThat(schema.get("preferredModality").get("example").asString()).isEqualTo("HYBRID");
        assertThat(schema.has("provenance")).isFalse();
        var summaryProvenance = schemas.get("ProfileResponse").get("properties").get("summaryProvenance");
        assertThat(summaryProvenance.get("enum").valueStream().map(v -> v.asString()).toList()).containsExactly("MANUAL");
        assertThat(response.body()).contains("PROFILE_NAME_REQUIRED", "PROFILE_NAME_INVALID_CHARACTERS",
                "SUMMARY_NOT_ALLOWED", "SUMMARY_INVALID_CHARACTERS", "PREFERRED_MODALITY_INVALID_VALUE");
    }

    @Test
    @DisplayName("El OpenAPI de la formación publica los límites de 150, el formato de las fechas, los niveles y sin área de estudio")
    void addEducationRequest_shouldDocumentLimits_whenApiDocsAreGenerated() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        var schemas = JsonMapper.builder().build().readTree(response.body()).get("components").get("schemas");
        var schema = schemas.get("AddEducationRequest").get("properties");
        assertThat(schema.get("institution").get("maxLength").asInt()).isEqualTo(150);
        assertThat(schema.get("degree").get("maxLength").asInt()).isEqualTo(150);
        assertThat(schema.get("level").get("enum").valueStream().map(v -> v.asString()).toList())
                .containsExactlyInAnyOrder("TECHNICAL", "UNDERGRADUATE", "POSTGRADUATE");
        assertThat(schema.get("startDate").get("pattern").asString()).isEqualTo("^\\d{4}-(0[1-9]|1[0-2])$");
        assertThat(schema.get("endDate").get("pattern").asString()).isEqualTo("^\\d{4}-(0[1-9]|1[0-2])$");
        assertThat(schema.has("provenance")).isFalse();
        assertThat(schema.has("fieldOfStudy")).isFalse();
        assertThat(schemas.get("EducationItem").get("properties").has("fieldOfStudy")).isFalse();
        assertThat(response.body()).contains("EDUCATION_LIMIT_REACHED", "EDUCATION_NOT_ALLOWED");
    }

    @Test
    @DisplayName("El OpenAPI de la habilidad publica el límite de 60, los niveles, sin procedencia, y los códigos 409")
    void addSkillRequest_shouldDocumentLimits_whenApiDocsAreGenerated() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        var schema = JsonMapper.builder().build().readTree(response.body()).get("components").get("schemas")
                .get("AddSkillRequest").get("properties");
        assertThat(schema.get("skillName").get("maxLength").asInt()).isEqualTo(60);
        assertThat(schema.get("skillName").get("minLength").asInt()).isEqualTo(1);
        assertThat(schema.get("level").get("enum").valueStream().map(v -> v.asString()).toList())
                .containsExactlyInAnyOrder("BASIC", "INTERMEDIATE", "ADVANCED");
        assertThat(schema.has("provenance")).isFalse();
        assertThat(response.body()).contains("SKILL_LIMIT_REACHED", "SKILL_NOT_ALLOWED", "SKILL_ALREADY_EXISTS",
                "SKILL_NAME_INVALID_CHARACTERS");
    }

    @Test
    @DisplayName("El OpenAPI de la finalización publica los cinco requisitos, sus códigos y no publica review-requests")
    void completion_shouldDocumentRequirements_whenApiDocsAreGenerated() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        var schemas = JsonMapper.builder().build().readTree(response.body()).get("components").get("schemas");
        var missing = schemas.get("ApiError").get("properties").get("missingRequirements").get("items");
        assertThat(missing.get("enum").valueStream().map(v -> v.asString()).toList())
                .containsExactly("NAME", "SUMMARY", "EDUCATION", "SKILLS", "TARGET_ROLES");
        assertThat(response.body()).contains("PROFILE_INCOMPLETE", "COMPLETION_TARGET_ROLES_REQUIRED", "PROFILE_ALREADY_COMPLETED")
                .doesNotContain("review-requests");
    }
}
