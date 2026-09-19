package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                        .header("X-User-Id", "uid-ctrl-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Sofía"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Sofía"));
    }

    // ── CM-174 ────────────────────────────────────────────────────────────

    @Test
    void getProfile_returns403WhenProfileIsOwnedByAnotherUser() throws Exception {
        when(profileAppService.getProfile(any(UUID.class), any(String.class)))
                .thenThrow(new ProfileAccessDeniedException());

        mockMvc.perform(get("/api/v1/profiles/{id}", UUID.randomUUID())
                        .header("X-User-Id", "uid-other"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void getProfile_returns401WhenXUserIdIsMissing() throws Exception {
        when(profileAppService.getProfile(any(UUID.class), any()))
                .thenThrow(new IdentityRequiredException());

        mockMvc.perform(get("/api/v1/profiles/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
    }

    @Test
    void patchProfile_returns403WhenProfileIsOwnedByAnotherUser() throws Exception {
        when(profileAppService.updateProfileInfo(any()))
                .thenThrow(new ProfileAccessDeniedException());

        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .header("X-User-Id", "uid-other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Intruso"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void patchProfile_returns401WhenXUserIdIsMissing() throws Exception {
        when(profileAppService.updateProfileInfo(any()))
                .thenThrow(new IdentityRequiredException());

        mockMvc.perform(patch("/api/v1/profiles/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sin uid"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
    }

    // ── CM-19 ─────────────────────────────────────────────────────────────

    @Test
    void patchSalaryExpectation_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-sal"));
        when(profileAppService.updateSalaryExpectation(any())).thenReturn(profile);

        mockMvc.perform(patch("/api/v1/profiles/{id}/salary-expectation", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-sal")
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
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "level": "EXPERT", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void postSkills_returns409WhenDuplicate() throws Exception {
        when(profileAppService.addSkill(any())).thenThrow(new DuplicateSkillException("Java"));

        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "level": "ADVANCED", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Habilidad duplicada"));
    }

    @Test
    void postSkills_returns400WhenLevelIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/profiles/{id}/skills", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName": "Java", "provenance": "MANUAL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSkill_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delskill"));
        when(profileAppService.removeSkill(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/skills/{skillId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delskill"))
                .andExpect(status().isOk());
    }

    @Test
    void postReviewRequests_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-rev"));
        when(profileAppService.requestReview(any(), any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/review-requests", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-rev"))
                .andExpect(status().isCreated());
    }

    @Test
    void postReviewRequests_returns422WhenProfileIncomplete() throws Exception {
        when(profileAppService.requestReview(any(), any()))
                .thenThrow(new IncompleteProfileException(List.of("nombre", "resumen")));

        mockMvc.perform(post("/api/v1/profiles/{id}/review-requests", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-rev"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.missingRequirements").isArray());
    }

    // ── CM-20 / CM-21 / CM-23 ─────────────────────────────────────────────

    @Test
    void postTargetRoles_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-role"));
        when(profileAppService.addTargetRole(any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    void postTargetRoles_returns409WhenDuplicate() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new DuplicateTargetRoleException("Backend Developer"));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Rol objetivo duplicado"));
    }

    @Test
    void postTargetRoles_returns422WhenMaxExceeded() throws Exception {
        when(profileAppService.addTargetRole(any())).thenThrow(new MaxTargetRolesExceededException(5));

        mockMvc.perform(post("/api/v1/profiles/{id}/target-roles", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRoleId": "%s", "provenance": "MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Máximo de roles objetivo alcanzado"));
    }

    @Test
    void deleteTargetRole_returns200() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delrole"));
        when(profileAppService.removeTargetRole(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(delete("/api/v1/profiles/{id}/target-roles/{roleId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-delrole"))
                .andExpect(status().isOk());
    }

    // ── CM-22 ─────────────────────────────────────────────────────────────

    @Test
    void postCompletion_returns201() throws Exception {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-comp"));
        when(profileAppService.completeProfile(any(), any())).thenReturn(profile);

        mockMvc.perform(post("/api/v1/profiles/{id}/completion", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-comp"))
                .andExpect(status().isCreated());
    }

    @Test
    void postCompletion_returns422WhenIncomplete() throws Exception {
        when(profileAppService.completeProfile(any(), any()))
                .thenThrow(new IncompleteProfileException(List.of("nombre", "resumen", "educacion")));

        mockMvc.perform(post("/api/v1/profiles/{id}/completion", UUID.randomUUID())
                        .header("X-User-Id", "uid-ctrl-comp"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.missingRequirements").isArray());
    }
}
