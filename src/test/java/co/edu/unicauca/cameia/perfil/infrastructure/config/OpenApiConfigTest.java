package co.edu.unicauca.cameia.perfil.infrastructure.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenApiConfig: respuestas de error comunes")
class OpenApiConfigTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private Operation customize(ApiResponse existing503) {
        var operation = new Operation().responses(new ApiResponses());
        if (existing503 != null) {
            operation.getResponses().addApiResponse("503", existing503);
        }
        return new OpenApiConfig().protocolErrorResponses().customize(operation, null);
    }

    @Test
    @DisplayName("Sin 503 previo, la operación recibe el de la base de datos")
    void protocolErrorResponses_shouldAddDatabase503_whenOperationHasNone() {
        var response = customize(null).getResponses().get("503");

        assertThat(response.getDescription()).contains("DATABASE_UNAVAILABLE");
        assertThat(response.getContent().get(PROBLEM_JSON).getExamples()).containsKey("baseNoDisponible");
    }

    @Test
    @DisplayName("Un 503 previo sin contenido recibe el contenido de la base de datos y conserva su descripción")
    void protocolErrorResponses_shouldKeepDescription_whenExisting503HasNoContent() {
        var response = customize(new ApiResponse().description("BIRTH_DATE_UNAVAILABLE")).getResponses().get("503");

        assertThat(response.getDescription()).contains("DATABASE_UNAVAILABLE").contains("BIRTH_DATE_UNAVAILABLE");
        assertThat(response.getContent().get(PROBLEM_JSON).getExamples()).containsKey("baseNoDisponible");
    }

    @Test
    @DisplayName("Un 503 previo con otro tipo de contenido recibe además el problem+json")
    void protocolErrorResponses_shouldAddProblemJson_whenExisting503HasOtherMediaType() {
        var existing = new ApiResponse().description("BIRTH_DATE_UNAVAILABLE")
                .content(new Content().addMediaType("application/json", new MediaType()));

        var content = customize(existing).getResponses().get("503").getContent();

        assertThat(content.get("application/json")).isNotNull();
        assertThat(content.get(PROBLEM_JSON).getExamples()).containsKey("baseNoDisponible");
    }

    @Test
    @DisplayName("Un 503 previo con problem+json conserva sus ejemplos y suma el de la base de datos")
    void protocolErrorResponses_shouldMergeExamples_whenExisting503HasProblemJson() {
        var existing = new ApiResponse().description("BIRTH_DATE_UNAVAILABLE").content(new Content()
                .addMediaType(PROBLEM_JSON, new MediaType().addExamples("fechaNoDisponible",
                        new io.swagger.v3.oas.models.examples.Example().value("x"))));

        var examples = customize(existing).getResponses().get("503").getContent().get(PROBLEM_JSON).getExamples();

        assertThat(examples).containsKeys("fechaNoDisponible", "baseNoDisponible");
    }
}
