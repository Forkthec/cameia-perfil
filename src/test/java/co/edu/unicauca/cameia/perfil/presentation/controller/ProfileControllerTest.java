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
import co.edu.unicauca.cameia.perfil.presentation.dto.AddSkillRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateProfileInfoRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateSalaryExpectationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @WebMvcTest no existe en Spring Boot 4.1.1 — se usa Mockito puro.
// Las pruebas HTTP-level (JSON serialization, headers) se validan con Postman.
@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock ProfileAppService profileAppService;
    @InjectMocks ProfileController controller;

    // ── CM-16 ─────────────────────────────────────────────────────────────

    @Test
    void createProfile_delegatesAndReturns201() {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-001"));
        when(profileAppService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile);

        var response = controller.createProfile("uid-ctrl-001");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("IN_PROGRESS");
        verify(profileAppService).createProfile(new CreateProfileCommand("uid-ctrl-001"));
    }

    @Test
    void createProfile_propagatesAlreadyExistsException() {
        when(profileAppService.createProfile(any())).thenThrow(new ProfileAlreadyExistsException());
        assertThatThrownBy(() -> controller.createProfile("uid-dup"))
                .isInstanceOf(ProfileAlreadyExistsException.class);
    }

    // ── CM-17 ─────────────────────────────────────────────────────────────

    @Test
    void updateProfileInfo_delegatesAndReturns200() {
        var id = UUID.randomUUID();
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-002"));
        profile.updateName(new ProfileName("Ana Sofía"));
        when(profileAppService.updateProfileInfo(any(UpdateProfileInfoCommand.class))).thenReturn(profile);

        var request = new UpdateProfileInfoRequest("Ana Sofía", null, null, null, null);
        var response = controller.updateProfileInfo(id, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Ana Sofía");
        verify(profileAppService).updateProfileInfo(any(UpdateProfileInfoCommand.class));
    }

    // ── CM-19 ─────────────────────────────────────────────────────────────

    @Test
    void updateSalaryExpectation_delegatesAndReturns200() {
        var id = UUID.randomUUID();
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-sal"));
        when(profileAppService.updateSalaryExpectation(any())).thenReturn(profile);

        var request = new UpdateSalaryExpectationRequest(new BigDecimal("3500000"));
        var response = controller.updateSalaryExpectation(id, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(profileAppService).updateSalaryExpectation(any());
    }

    @Test
    void addSkill_delegatesAndReturns201() {
        var id = UUID.randomUUID();
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-skill"));
        when(profileAppService.addSkill(any())).thenReturn(profile);

        var request = new AddSkillRequest("Java", "EXPERT", "MANUAL");
        var response = controller.addSkill(id, request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(profileAppService).addSkill(any());
    }

    @Test
    void removeSkill_delegatesAndReturns200() {
        var id = UUID.randomUUID();
        var skillId = UUID.randomUUID();
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-delskill"));
        when(profileAppService.removeSkill(id, skillId)).thenReturn(profile);

        var response = controller.removeSkill(id, skillId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(profileAppService).removeSkill(id, skillId);
    }

    @Test
    void requestReview_delegatesAndReturns201() {
        var id = UUID.randomUUID();
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-rev"));
        when(profileAppService.requestReview(id)).thenReturn(profile);

        var response = controller.requestReview(id);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(profileAppService).requestReview(id);
    }

    @Test
    void requestReview_propagatesIncompleteProfileException() {
        var id = UUID.randomUUID();
        when(profileAppService.requestReview(id)).thenThrow(new IncompleteProfileException());
        assertThatThrownBy(() -> controller.requestReview(id))
                .isInstanceOf(IncompleteProfileException.class);
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
        when(profileAppService.addTargetRole(any())).thenThrow(new DuplicateTargetRoleException("Duplicado"));

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
        when(profileAppService.addTargetRole(any())).thenThrow(new MaxTargetRolesExceededException("Máximo alcanzado"));

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
