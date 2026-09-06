package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de capa de presentación con slice {@code @WebMvcTest}.
 * {@code ProfileAppService} se reemplaza por un mock: sin BD, rápido.
 */
@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProfileAppService profileAppService;

    // ── CM-16 ─────────────────────────────────────────────────────────────

    @Test
    void postProfiles_returns201WithProfileBody() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-001"));
        when(profileAppService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles")
                        .header("X-User-Id", "uid-ctrl-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.targetRoles").isArray())
                .andExpect(jsonPath("$.workExperiences").isArray());
    }

    @Test
    void postProfiles_returns409WhenProfileAlreadyExists() throws Exception {
        when(profileAppService.createProfile(any())).thenThrow(new ProfileAlreadyExistsException());

        mockMvc.perform(post("/api/v1/profiles")
                        .header("X-User-Id", "uid-dup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Perfil ya existe"));
    }

    @Test
    void postProfiles_returns400WhenXUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/profiles"))
                .andExpect(status().isBadRequest());
    }
}
