package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
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
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAppServiceTest {

    static final String SVC_UID = "firebase-svc-test";
    static final String COMPLETE_UID = "firebase-complete";

    @Mock ProfessionalProfileRepository repository;
    @Mock ProfessionalRoleRepository roleRepository;

    ProfileAppService service;

    @BeforeEach
    void setUp() {
        service = new ProfileAppService(repository, roleRepository,
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres se rechaza como identidad ausente, no como acceso denegado")
    void getProfile_shouldThrowIdentityRequired_whenUidIsTooLong() {
        assertThatThrownBy(() -> service.getProfile(UUID.randomUUID(), "a".repeat(129)))
                .isInstanceOf(IdentityRequiredException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    void updateProfileInfo_appliesNameAndSummary() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, "Ana Sofía", "Dev backend", null, null));
        assertThat(profile.getName().value()).isEqualTo("Ana Sofía");
        assertThat(profile.getSummary().value()).isEqualTo("Dev backend");
        verify(repository).save(profile);
    }

    @Test
    void updateProfileInfo_throwsNotFoundWhenProfileMissing() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfileInfo(
                new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateProfileInfo_nullFieldsDoNotOverwriteExistingValues() {
        var profile = freshProfile();
        profile.updateName(new ProfileName("Nombre original"));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, "nuevo resumen", null, null));
        assertThat(profile.getName().value()).isEqualTo("Nombre original");
        assertThat(profile.getSummary().value()).isEqualTo("nuevo resumen");
    }

    @Test
    void addWorkExperience_addsEntryAndSaves() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2022-01", null, "CURRENT", "MANUAL"));
        assertThat(profile.getWorkExperiences()).hasSize(1);
        assertThat(profile.getWorkExperiences().get(0).getCompany()).isEqualTo("ACME");
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Una fecha de inicio con mes 13 no guarda la experiencia y marca su campo")
    void addWorkExperience_shouldThrowStartDateInvalid_whenStartDateIsMalformed() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2020-13", null, "CURRENT", "MANUAL")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("startDate", ErrorCode.START_DATE_INVALID_FORMAT)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un estado laboral que no existe no guarda la experiencia y marca su campo")
    void addWorkExperience_shouldThrowEmploymentStatusInvalid_whenStatusIsUnknown() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2020-01", null, "FREELANCE", "MANUAL")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("employmentStatus", ErrorCode.EMPLOYMENT_STATUS_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una fecha de fin anterior al inicio no guarda la experiencia")
    void addWorkExperience_shouldThrowEndDateBeforeStart_whenEndIsBeforeStart() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2022-05", "2021-01", "ENDED", "MANUAL")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("endDate", ErrorCode.END_DATE_BEFORE_START_DATE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una procedencia que no existe no guarda la habilidad")
    void addSkill_shouldThrowProvenanceInvalid_whenProvenanceIsUnknown() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addSkill(new AddSkillCommand(
                UUID.randomUUID(), SVC_UID, "Java", "BASIC", "HUMANO")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("provenance", ErrorCode.PROVENANCE_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una educación con solo el año de inicio se guarda en enero de ese año")
    void addEducation_shouldReadJanuary_whenStartDateIsOnlyYear() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.addEducation(new AddEducationCommand(UUID.randomUUID(), SVC_UID, "Unicauca", "Ingeniería",
                null, "UNDERGRADUATE", "2018", null, false, "MANUAL"));
        assertThat(profile.getEducations()).singleElement()
                .satisfies(e -> assertThat(e.getStartDate()).isEqualTo(YearMonth.of(2018, 1)));
    }

    @Test
    @DisplayName("Un nivel de formación que no existe no guarda la educación")
    void addEducation_shouldThrowEducationLevelInvalid_whenLevelIsUnknown() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(UUID.randomUUID(), SVC_UID,
                "Unicauca", "Ingeniería", null, "DOCTORADO", "2018", null, false, "MANUAL")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("level", ErrorCode.EDUCATION_LEVEL_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un resumen con solo espacios borra el resumen del perfil en Borrador (CA-2.3.4)")
    void updateProfileInfo_shouldClearSummary_whenSummaryIsBlank() {
        var profile = freshProfile();
        profile.updateSummary(new ProfessionalSummary("Resumen anterior"));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, "   ", null, null));

        assertThat(profile.getSummary()).isNull();
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Una modalidad y una procedencia de la lista se guardan en el perfil")
    void updateProfileInfo_shouldApplyModalityAndProvenance_whenBothAreOptions() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), SVC_UID, null, null, "REMOTE", "AI_EDITED"));
        assertThat(profile.getPreferredModality()).isEqualTo(WorkModality.REMOTE);
        assertThat(profile.getProvenance()).isEqualTo(DataProvenance.AI_EDITED);
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Editar un rol objetivo del perfil lo cambia por el rol del catálogo")
    void updateTargetRole_shouldReplaceRole_whenRoleIsInProfile() {
        var profile = freshProfile();
        profile.addTargetRole(new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Backend", DataProvenance.MANUAL));
        var roleId = profile.getTargetRoles().get(0).getId();
        var catalogRole = new ProfessionalRole(UUID.randomUUID(), "Frontend Developer", "Desarrollo");
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(catalogRole.id())).thenReturn(Optional.of(catalogRole));

        service.updateTargetRole(new UpdateTargetRoleCommand(UUID.randomUUID(), SVC_UID, roleId, catalogRole.id()));

        assertThat(profile.getTargetRoles()).singleElement()
                .satisfies(r -> assertThat(r.getRoleTitle()).isEqualTo("Frontend Developer"));
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Editar un rol objetivo que no está en el perfil no guarda nada")
    void updateTargetRole_shouldThrowTargetRoleNotFound_whenRoleIsNotInProfile() {
        var catalogRole = new ProfessionalRole(UUID.randomUUID(), "Frontend Developer", "Desarrollo");
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        when(roleRepository.findById(catalogRole.id())).thenReturn(Optional.of(catalogRole));

        assertThatThrownBy(() -> service.updateTargetRole(new UpdateTargetRoleCommand(
                UUID.randomUUID(), SVC_UID, UUID.randomUUID(), catalogRole.id())))
                .isInstanceOf(TargetRoleNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una modalidad preferida que no existe no actualiza el perfil")
    void updateProfileInfo_shouldThrowModalityInvalid_whenModalityIsUnknown() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), SVC_UID, null, null, "PRESENCIAL", null)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("preferredModality", ErrorCode.PREFERRED_MODALITY_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    void removeWorkExperience_removesFromProfileAndSaves() {
        var profile = freshProfile();
        profile.addWorkExperience(new co.edu.unicauca.cameia.perfil.domain.model.WorkExperience(
                UUID.randomUUID(), "ACME", "Dev", null,
                YearMonth.of(2022, 1), null,
                EmploymentStatus.CURRENT, DataProvenance.MANUAL));
        var expId = profile.getWorkExperiences().get(0).getId();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.removeWorkExperience(UUID.randomUUID(), SVC_UID, expId);
        assertThat(profile.getWorkExperiences()).isEmpty();
        verify(repository).save(profile);
    }

    @Test
    void updateSalaryExpectation_setsAmountAndSaves() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateSalaryExpectation(new UpdateSalaryExpectationCommand(UUID.randomUUID(), SVC_UID, new BigDecimal("3500000")));
        assertThat(profile.getSalaryExpectation().amount()).isEqualByComparingTo("3500000");
        verify(repository).save(profile);
    }

    @Test
    void addSkill_addsSkillAndSaves() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.addSkill(new AddSkillCommand(UUID.randomUUID(), SVC_UID, "Java", "ADVANCED", "MANUAL"));
        assertThat(profile.getProfileSkills()).hasSize(1);
        assertThat(profile.getProfileSkills().get(0).getSkillName()).isEqualTo("Java");
        verify(repository).save(profile);
    }

    @Test
    void removeSkill_removesSkillAndSaves() {
        var profile = freshProfile();
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));
        var skillId = profile.getProfileSkills().get(0).getId();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.removeSkill(UUID.randomUUID(), SVC_UID, skillId);
        assertThat(profile.getProfileSkills()).isEmpty();
        verify(repository).save(profile);
    }

    @Test
    void requestReview_throwsIncompleteWhenProfileLacksRequiredFields() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.requestReview(UUID.randomUUID(), SVC_UID))
                .isInstanceOf(IncompleteProfileException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void requestReview_changesStatusToInReview() {
        var profile = completeProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.requestReview(UUID.randomUUID(), COMPLETE_UID);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_REVIEW);
        verify(repository).save(profile);
    }

    @Test
    void addTargetRole_addsRoleAndSaves() {
        var profile = freshProfile();
        var roleId = UUID.randomUUID();
        var catRole = new ProfessionalRole(roleId, "Backend Developer", "Desarrollo");
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(catRole));
        service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), SVC_UID, roleId, "MANUAL"));
        assertThat(profile.getTargetRoles()).hasSize(1);
        assertThat(profile.getTargetRoles().get(0).getRoleTitle()).isEqualTo("Backend Developer");
        verify(repository).save(profile);
    }

    @Test
    void removeTargetRole_throwsLastTargetRoleWhenOnlyOneOnCompletedProfile() {
        var profile = completeProfile();
        profile.complete();
        var roleId = profile.getTargetRoles().get(0).getId();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.removeTargetRole(UUID.randomUUID(), COMPLETE_UID, roleId))
                .isInstanceOf(LastTargetRoleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void completeProfile_setsStatusToCompleted() {
        var profile = completeProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.completeProfile(UUID.randomUUID(), COMPLETE_UID);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
        verify(repository).save(profile);
    }

    @Test
    void completeProfile_throwsIncompleteWhenNotReady() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.completeProfile(UUID.randomUUID(), SVC_UID))
                .isInstanceOf(IncompleteProfileException.class);
        verify(repository, never()).save(any());
    }

    // ── bloqueo del perfil en toda escritura ───────────────────────────────

    /** Los 13 casos de uso que modifican un perfil, cada uno con una llamada que no necesita datos válidos. */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> writeUseCases() {
        var id = UUID.randomUUID();
        return java.util.stream.Stream.of(
                use("updateProfileInfo", s -> s.updateProfileInfo(new UpdateProfileInfoCommand(id, SVC_UID, null, null, null, null))),
                use("addWorkExperience", s -> s.addWorkExperience(new AddWorkExperienceCommand(
                        id, SVC_UID, "ACME", "Dev", null, "2022-01", null, "CURRENT", "MANUAL"))),
                use("removeWorkExperience", s -> s.removeWorkExperience(id, SVC_UID, UUID.randomUUID())),
                use("addEducation", s -> s.addEducation(new AddEducationCommand(id, SVC_UID, "Unicauca", "Ingeniería",
                        null, "UNDERGRADUATE", "2018-01", null, false, "MANUAL"))),
                use("removeEducation", s -> s.removeEducation(id, SVC_UID, UUID.randomUUID())),
                use("updateSalaryExpectation", s -> s.updateSalaryExpectation(
                        new UpdateSalaryExpectationCommand(id, SVC_UID, new BigDecimal("3500000")))),
                use("addSkill", s -> s.addSkill(new AddSkillCommand(id, SVC_UID, "Java", "ADVANCED", "MANUAL"))),
                use("removeSkill", s -> s.removeSkill(id, SVC_UID, UUID.randomUUID())),
                use("requestReview", s -> s.requestReview(id, SVC_UID)),
                use("addTargetRole", s -> s.addTargetRole(new AddTargetRoleCommand(id, SVC_UID, UUID.randomUUID(), "MANUAL"))),
                use("updateTargetRole", s -> s.updateTargetRole(
                        new UpdateTargetRoleCommand(id, SVC_UID, UUID.randomUUID(), UUID.randomUUID()))),
                use("removeTargetRole", s -> s.removeTargetRole(id, SVC_UID, UUID.randomUUID())),
                use("completeProfile", s -> s.completeProfile(id, SVC_UID)));
    }

    private static org.junit.jupiter.params.provider.Arguments use(String name,
            java.util.function.Consumer<ProfileAppService> call) {
        return org.junit.jupiter.params.provider.Arguments.of(name, call);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("writeUseCases")
    @DisplayName("Toda escritura carga el perfil bloqueando su fila")
    void writes_shouldLoadProfileForUpdate_whenProfileChanges(String name, java.util.function.Consumer<ProfileAppService> call) {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> call.accept(service)).isInstanceOf(ProfileNotFoundException.class);

        verify(repository).findByIdForUpdate(any());
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("Leer un perfil no bloquea su fila")
    void getProfile_shouldNotLock_whenReading() {
        when(repository.findById(any())).thenReturn(Optional.of(freshProfile()));

        service.getProfile(UUID.randomUUID(), SVC_UID);

        verify(repository).findById(any());
        verify(repository, never()).findByIdForUpdate(any());
    }

    // ── loadProfile ───────────────────────────────────────────────────────

    @Test
    void loadProfile_throwsNotFoundWhenProfileDoesNotExist() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> service.loadProfile(id))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessage("No encontramos lo que buscabas.");
    }

    @Test
    @DisplayName("Un perfil de otro Usuario se rechaza como acceso denegado")
    void getProfile_shouldThrowAccessDenied_whenProfileBelongsToAnotherUser() {
        when(repository.findById(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.getProfile(UUID.randomUUID(), "uid-otra-persona"))
                .isInstanceOf(ProfileAccessDeniedException.class);
    }

    private static ProfessionalProfile freshProfile() {
        return ProfessionalProfile.create(new FirebaseUid(SVC_UID));
    }

    private static ProfessionalProfile completeProfile() {
        var p = ProfessionalProfile.create(new FirebaseUid(COMPLETE_UID));
        p.updateName(new ProfileName("Ana Sofía"));
        p.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Java y DDD."));
        p.addTargetRole(new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Backend Developer", DataProvenance.MANUAL));
        p.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));
        p.addEducation(new Education(UUID.randomUUID(), "Universidad X", "Ingeniería", "Sistemas",
                EducationLevel.UNDERGRADUATE, YearMonth.of(2018, 1), null, false, DataProvenance.MANUAL));
        return p;
    }
}
