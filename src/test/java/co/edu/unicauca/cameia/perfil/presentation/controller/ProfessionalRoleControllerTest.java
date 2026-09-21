package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.service.ProfessionalRoleAppService;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.presentation.advice.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

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

    @Test
    void listAll_returns400ForUnsupportedLang() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/professional-roles").param("lang", "fr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.detail").value("El valor 'fr' no es un idioma soportado. Use 'es' o 'en'."));
    }
}
