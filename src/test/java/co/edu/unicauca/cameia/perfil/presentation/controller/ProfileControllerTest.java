package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.presentation.advice.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock ProfileAppService profileAppService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(profileAppService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    // ── CM-16 ─────────────────────────────────────────────────────────────

    @Test
    void postProfiles_returns201WithProfileBody() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-001"));
        when(profileAppService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-ctrl-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.targetRoles").isArray());
    }

    @Test
    void postProfiles_returns409WhenProfileAlreadyExists() throws Exception {
        when(profileAppService.createProfile(any())).thenThrow(new ProfileAlreadyExistsException());

        mockMvc.perform(post("/api/v1/profiles").header("X-User-Id", "uid-dup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Perfil ya existe"));
    }

    @Test
    void postProfiles_returns400WhenXUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/profiles"))
                .andExpect(status().isBadRequest());
    }

    // ── CM-17 ─────────────────────────────────────────────────────────────

    @Test
    void patchProfile_returns200WithUpdatedProfile() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-002"));
        profile.updateName(new ProfileName("Ana Sofía"));
        when(profileAppService.updateProfileInfo(any(UpdateProfileInfoCommand.class))).thenReturn(profile);

        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Sofía"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Sofía"));
    }

    // ── CM-19 ─────────────────────────────────────────────────────────────

    @Test
    void patchSalaryExpectation_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-sal"));
        when(profileAppService.updateSalaryExpectation(any())).thenReturn(profile);

        mockMvc.perform(patch("/api/v1/profiles/{id}/salary-expectation", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 3500000}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void postSkills_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-skill"));
        when(profileAppService.addSkill(any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "level": "EXPERT", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteSkill_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delskill"));
        when(profileAppService.removeSkill(any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/skills/{skillId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void postReviewRequests_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-rev"));
        when(profileAppService.requestReview(any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/review-requests", UUID.randomUUID()))
                .andExpect(status().isCreated());
    }

    @Test
    void postReviewRequests_returns422WhenProfileIncomplete() throws Exception {
        when(profileAppService.requestReview(any())).thenThrow(new IncompleteProfileException());

        mockMvc.perform(post("/api/v1/profiles/{id}/review-requests", UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Perfil incompleto"));
    }

    // ── CM-20 ─────────────────────────────────────────────────────────────

    @Test
    void postTargetRoles_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-role"));
        when(profileAppService.addTargetRole(any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Backend Developer", "seniority": "JUNIOR", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void postTargetRoles_returns409WhenDuplicate() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new DuplicateTargetRoleException("Backend Developer"));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Backend Developer", "seniority": "JUNIOR", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Rol objetivo duplicado"));
    }

    @Test
    void postTargetRoles_returns422WhenMaxExceeded() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new MaxTargetRolesExceededException(5));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Extra Role", "seniority": "JUNIOR", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Máximo de roles objetivo alcanzado"));
    }

    @Test
    void deleteTargetRole_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delrole"));
        when(profileAppService.removeTargetRole(any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/target-roles/{roleId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
