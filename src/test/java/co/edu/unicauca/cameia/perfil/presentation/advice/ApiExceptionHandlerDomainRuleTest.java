package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.presentation.dto.validation.CodePointSize;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule;
import co.edu.unicauca.cameia.perfil.presentation.dto.validation.DomainRule.Rule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El manejador usa el código y el mensaje que una regla del dominio adjunta a la violación; las demás salen del catálogo. */
class ApiExceptionHandlerDomainRuleTest {

    /** Petición de prueba con una regla del dominio, una restricción de largo y una de ausencia. */
    record ProbeRequest(
            @NotBlank @DomainRule(Rule.COMPANY) String company,
            @CodePointSize(max = 5) String position) {
    }

    @RestController
    static class ProbeController {
        @PostMapping("/probe")
        void probe(@Valid @RequestBody ProbeRequest request) {
            // Solo interesa la validación del cuerpo.
        }
    }

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    @DisplayName("Una regla del dominio rechazada en el borde responde con su código y su mensaje")
    void fieldProblems_shouldUseDomainRuleCodeAndMessage_whenRuleRejects() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\\u0000\",\"position\":\"abc\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("company"))
                .andExpect(jsonPath("$.errors[0].code").value("COMPANY_INVALID_CHARACTERS"))
                .andExpect(jsonPath("$.errors[0].message").value("La empresa tiene caracteres no permitidos."));
    }

    @Test
    @DisplayName("Las restricciones sin regla del dominio salen del catálogo, con un solo error por campo")
    void fieldProblems_shouldUseCatalog_whenConstraintIsNotADomainRule() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"position\":\"abcdef\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='company')].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='position')].code").value("VALIDATION_FAILED"));
    }
}
