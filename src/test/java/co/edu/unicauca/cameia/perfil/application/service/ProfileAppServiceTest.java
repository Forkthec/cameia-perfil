package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAppServiceTest {

    @Mock ProfessionalProfileRepository repository;
    @InjectMocks ProfileAppService service;

    // ── CM-16 ────────────────────────────────────────────────────────────

    @Test
    void createProfile_savesProfileAndReturnsIt() {
        when(repository.existsByFirebaseUid(any())).thenReturn(false);
        var result = service.createProfile(new CreateProfileCommand("uid-001"));
        assertThat(result.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
        verify(repository).save(result);
    }

    @Test
    void createProfile_throwsAlreadyExistsWhenUidAlreadyRegistered() {
        when(repository.existsByFirebaseUid(any())).thenReturn(true);
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("uid-dup")))
                .isInstanceOf(ProfileAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createProfile_checksExistenceWithCorrectUid() {
        when(repository.existsByFirebaseUid(any())).thenReturn(false);
        service.createProfile(new CreateProfileCommand("uid-check"));
        var captor = ArgumentCaptor.forClass(FirebaseUid.class);
        verify(repository).existsByFirebaseUid(captor.capture());
        assertThat(captor.getValue().value()).isEqualTo("uid-check");
    }

    @Test
    void createProfile_rejectsBlankFirebaseUid() {
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).existsByFirebaseUid(any());
    }

    // ── CM-17 ────────────────────────────────────────────────────────────

    @Test
    void updateProfileInfo_appliesNameAndSummary() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), "Ana Sofía", null, "Dev backend", null, null));
        assertThat(profile.getName().value()).isEqualTo("Ana Sofía");
        assertThat(profile.getSummary().value()).isEqualTo("Dev backend");
        verify(repository).save(profile);
    }

    @Test
    void updateProfileInfo_throwsNotFoundWhenProfileMissing() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfileInfo(
                new UpdateProfileInfoCommand(UUID.randomUUID(), null, null, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateProfileInfo_nullFieldsDoNotOverwriteExistingValues() {
        var profile = freshProfile();
        profile.updateName(new ProfileName("Nombre original"));
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), null, "nuevo headline", null, null, null));
        assertThat(profile.getName().value()).isEqualTo("Nombre original");
        assertThat(profile.getHeadline()).isEqualTo("nuevo headline");
    }

    // ── CM-18 ────────────────────────────────────────────────────────────

    @Test
    void addWorkExperience_addsEntryAndSaves() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), "ACME", "Dev", null, "2022-01", null, "CURRENT", "JUNIOR", "MANUAL"));
        assertThat(profile.getWorkExperiences()).hasSize(1);
        assertThat(profile.getWorkExperiences().get(0).getCompany()).isEqualTo("ACME");
        verify(repository).save(profile);
    }

    @Test
    void removeWorkExperience_removesFromProfileAndSaves() {
        var profile = freshProfile();
        var cmd = new AddWorkExperienceCommand(UUID.randomUUID(), "ACME", "Dev", null, "2022-01", null, "CURRENT", "JUNIOR", "MANUAL");
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.addWorkExperience(cmd);
        var expId = profile.getWorkExperiences().get(0).getId();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.removeWorkExperience(UUID.randomUUID(), expId);
        assertThat(profile.getWorkExperiences()).isEmpty();
        verify(repository).save(profile);
    }

    // ── CM-19 ────────────────────────────────────────────────────────────

    @Test
    void updateSalaryExpectation_setsAmountAndSaves() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.updateSalaryExpectation(new UpdateSalaryExpectationCommand(UUID.randomUUID(), new BigDecimal("3500000")));
        assertThat(profile.getSalaryExpectation().amount()).isEqualByComparingTo("3500000");
        verify(repository).save(profile);
    }

    @Test
    void addSkill_addsSkillAndSaves() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.addSkill(new AddSkillCommand(UUID.randomUUID(), "Java", "EXPERT", "MANUAL"));
        assertThat(profile.getSkills()).hasSize(1);
        assertThat(profile.getSkills().get(0).getSkillName()).isEqualTo("Java");
        verify(repository).save(profile);
    }

    @Test
    void removeSkill_removesSkillAndSaves() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.addSkill(new AddSkillCommand(UUID.randomUUID(), "Java", "EXPERT", "MANUAL"));
        var skillId = profile.getSkills().get(0).getId();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.removeSkill(UUID.randomUUID(), skillId);
        assertThat(profile.getSkills()).isEmpty();
        verify(repository).save(profile);
    }

    @Test
    void requestReview_throwsIncompleteWhenProfileLacksRequiredFields() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.requestReview(UUID.randomUUID()))
                .isInstanceOf(IncompleteProfileException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void requestReview_changesStatusToInReview() {
        var profile = completeProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.requestReview(UUID.randomUUID());
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_REVIEW);
        verify(repository).save(profile);
    }

    // ── loadProfile ───────────────────────────────────────────────────────

    @Test
    void loadProfile_throwsNotFoundWhenProfileDoesNotExist() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> service.loadProfile(id))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    private static ProfessionalProfile freshProfile() {
        return ProfessionalProfile.create(new FirebaseUid("firebase-svc-test"));
    }

    private static ProfessionalProfile completeProfile() {
        var p = ProfessionalProfile.create(new FirebaseUid("firebase-complete"));
        p.updateName(new ProfileName("Ana Sofía"));
        p.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Java y DDD."));
        p.addTargetRole(new co.edu.unicauca.cameia.perfil.domain.model.TargetRole(
                UUID.randomUUID(), "Backend Developer", co.edu.unicauca.cameia.perfil.domain.model.Seniority.JUNIOR,
                co.edu.unicauca.cameia.perfil.domain.model.DataProvenance.MANUAL));
        return p;
    }
}
