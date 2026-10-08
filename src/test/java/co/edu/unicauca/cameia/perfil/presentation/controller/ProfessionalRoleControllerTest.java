package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.service.ProfessionalRoleAppService;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.presentation.advice.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfessionalRoleControllerTest {

    @Mock
    private ProfessionalRoleAppService service;

    private MockMvc mockMvc;

    private static final UUID ROLE_ID = UUID.fromString("a0000001-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfessionalRoleController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listAll_returnsSpanishByDefault() throws Exception {
        when(service.listAllByLang(eq("es")))
                .thenReturn(List.of(new ProfessionalRole(ROLE_ID, "Desarrollador Frontend", "Desarrollo")));

        mockMvc.perform(get("/api/v1/profiles/professional-roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Desarrollador Frontend"))
                .andExpect(jsonPath("$[0].categoria").value("Desarrollo"))
                .andExpect(jsonPath("$[0].id").value(ROLE_ID.toString()));
    }

    @Test
    void listAll_returnsEnglishWhenLangEn() throws Exception {
        when(service.listAllByLang(eq("en")))
                .thenReturn(List.of(new ProfessionalRole(ROLE_ID, "Frontend Developer", "Development")));

        mockMvc.perform(get("/api/v1/profiles/professional-roles").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Frontend Developer"))
                .andExpect(jsonPath("$[0].categoria").value("Development"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "ES", "es-CO"})
    @DisplayName("Un idioma no disponible responde 422 con su código, sin repetir el valor")
    void listAll_shouldReturn422_whenLangIsUnsupported(String lang) throws Exception {
        var result = mockMvc.perform(get("/api/v1/profiles/professional-roles").param("lang", lang))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE"))
                .andExpect(jsonPath("$.detail").value("Elige un idioma disponible: español o inglés."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"" + lang + "\"");
    }

    @Test
    @DisplayName("Un idioma vacío usa el español")
    void listAll_shouldReturnSpanish_whenLangIsEmpty() throws Exception {
        when(service.listAllByLang(eq("es")))
                .thenReturn(List.of(new ProfessionalRole(ROLE_ID, "Desarrollador Frontend", "Desarrollo")));

        mockMvc.perform(get("/api/v1/profiles/professional-roles").param("lang", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Desarrollador Frontend"));
    }
}
