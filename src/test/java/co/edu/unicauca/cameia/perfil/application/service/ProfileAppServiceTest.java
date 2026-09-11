package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
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
    @Mock ProfessionalRoleRepository roleRepository;

    ProfileAppService service;

    @BeforeEach
    void setUp() {
        service = new ProfileAppService(repository, roleRepository);
    }

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
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), "Ana Sofía", "Dev backend", null, null));
        assertThat(profile.getName().value()).isEqualTo("Ana Sofía");
        assertThat(profile.getSummary().value()).isEqualTo("Dev backend");
        verify(repository).save(profile);
    }

    @Test
    void updateProfileInfo_throwsNotFoundWhenProfileMissing() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfileInfo(
                new UpdateProfileInfoCommand(UUID.randomUUID(), null, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateProfileInfo_nullFieldsDoNotOverwriteExistingValues() {
        var profile = freshProfile();
        profile.updateName(new ProfileName("Nombre original"));
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), null, "nuevo resumen", null, null));
        assertThat(profile.getName().value()).isEqualTo("Nombre original");
        assertThat(profile.getSummary().value()).isEqualTo("nuevo resumen");
    }

    // ── CM-18 ────────────────────────────────────────────────────────────

    @Test
    void addWorkExperience_addsEntryAndSaves() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), "ACME", "Dev", null, "2022-01", null, "CURRENT", "MANUAL"));
        assertThat(profile.getWorkExperiences()).hasSize(1);
        assertThat(profile.getWorkExperiences().get(0).getCompany()).isEqualTo("ACME");
        verify(repository).save(profile);
    }

    @Test
    void removeWorkExperience_removesFromProfileAndSaves() {
        var profile = freshProfile();
        profile.addWorkExperience(new co.edu.unicauca.cameia.perfil.domain.model.WorkExperience(
                UUID.randomUUID(), "ACME", "Dev", null,
                YearMonth.of(2022, 1), null,
                EmploymentStatus.CURRENT, DataProvenance.MANUAL));
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
        service.addSkill(new AddSkillCommand(UUID.randomUUID(), "Java", "ADVANCED", "MANUAL"));
        assertThat(profile.getProfileSkills()).hasSize(1);
        assertThat(profile.getProfileSkills().get(0).getSkillName()).isEqualTo("Java");
        verify(repository).save(profile);
    }

    @Test
    void removeSkill_removesSkillAndSaves() {
        var profile = freshProfile();
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));
        var skillId = profile.getProfileSkills().get(0).getId();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.removeSkill(UUID.randomUUID(), skillId);
        assertThat(profile.getProfileSkills()).isEmpty();
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

    // ── CM-20 / CM-21 / CM-23 ────────────────────────────────────────────

    @Test
    void addTargetRole_addsRoleAndSaves() {
        var profile = freshProfile();
        var roleId = UUID.randomUUID();
        var catRole = new ProfessionalRole(roleId, "Backend Developer", "Desarrollo");
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(catRole));
        service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), roleId, "MANUAL"));
        assertThat(profile.getTargetRoles()).hasSize(1);
        assertThat(profile.getTargetRoles().get(0).getRoleTitle()).isEqualTo("Backend Developer");
        verify(repository).save(profile);
    }

    @Test
    void removeTargetRole_throwsLastTargetRoleWhenOnlyOneOnCompletedProfile() {
        var profile = completeProfile();
        profile.complete();
        var roleId = profile.getTargetRoles().get(0).getId();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.removeTargetRole(UUID.randomUUID(), roleId))
                .isInstanceOf(LastTargetRoleException.class);
        verify(repository, never()).save(any());
    }

    // ── CM-22 ────────────────────────────────────────────────────────────

    @Test
    void completeProfile_setsStatusToCompleted() {
        var profile = completeProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        service.completeProfile(UUID.randomUUID());
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
        verify(repository).save(profile);
    }

    @Test
    void completeProfile_throwsIncompleteWhenNotReady() {
        var profile = freshProfile();
        when(repository.findById(any())).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.completeProfile(UUID.randomUUID()))
                .isInstanceOf(IncompleteProfileException.class);
        verify(repository, never()).save(any());
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

    // ── Helpers ──────────────────────────────────────────────────────────

    private static ProfessionalProfile freshProfile() {
        return ProfessionalProfile.create(new FirebaseUid("firebase-svc-test"));
    }

    private static ProfessionalProfile completeProfile() {
        var p = ProfessionalProfile.create(new FirebaseUid("firebase-complete"));
        p.updateName(new ProfileName("Ana Sofía"));
        p.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Java y DDD."));
        p.addTargetRole(new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Backend Developer", DataProvenance.MANUAL));
        p.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));
        p.addEducation(new Education(UUID.randomUUID(), "Universidad X", "Ingeniería", "Sistemas",
                EducationLevel.UNDERGRADUATE, YearMonth.of(2018, 1), null, false, DataProvenance.MANUAL));
        return p;
    }
}
