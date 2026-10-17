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
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileStatusInvalidException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.DateBounds;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationDraft;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.exception.BirthDateUnavailableException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.InMemoryBirthDateReplica;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSummary;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.SkillName;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAppServiceTest {

    static final String SVC_UID = "firebase-svc-test";
    static final String COMPLETE_UID = "firebase-complete";

    @Mock ProfessionalProfileRepository repository;
    @Mock ProfessionalRoleRepository roleRepository;
    @Mock ProfileEventsAppService events;

    /** Réplica en memoria: las pruebas deciden qué Usuarios tienen fecha de nacimiento. */
    final InMemoryBirthDateReplica replica = spy(new InMemoryBirthDateReplica());

    ProfileAppService service;

    @BeforeEach
    void setUp() {
        replica.saveIfAbsent(new FirebaseUid(SVC_UID), LocalDate.of(1990, 1, 1));
        replica.saveIfAbsent(new FirebaseUid(COMPLETE_UID), LocalDate.of(1990, 1, 1));
        service = new ProfileAppService(repository, roleRepository,
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC), replica, events);
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres se rechaza como identidad ausente, no como acceso denegado")
    void getProfile_shouldThrowIdentityRequired_whenUidIsTooLong() {
        assertThatThrownBy(() -> service.getProfile(UUID.randomUUID(), "a".repeat(129)))
                .isInstanceOf(IdentityRequiredException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("La lista devuelve lo que entrega el repositorio, para el dueño de la identidad y sin filtro")
    void listProfiles_shouldReturnRepositoryResult() {
        var summaries = List.of(new ProfileSummary(ProfileId.generate(), "Analista", ProfileStatus.COMPLETED,
                Instant.parse("2026-10-01T10:00:00Z"), List.of()));
        when(repository.findSummariesByOwner(new FirebaseUid("uid-a"), null)).thenReturn(summaries);

        assertThat(service.listProfiles("uid-a", null)).isSameAs(summaries);
    }

    @Test
    @DisplayName("Con un estado exacto la lista se pide filtrada por ese estado")
    void listProfiles_shouldPassStatus_whenGiven() {
        when(repository.findSummariesByOwner(new FirebaseUid("uid-a"), ProfileStatus.COMPLETED)).thenReturn(List.of());

        assertThat(service.listProfiles("uid-a", "COMPLETED")).isEmpty();

        verify(repository).findSummariesByOwner(new FirebaseUid("uid-a"), ProfileStatus.COMPLETED);
    }

    @Test
    @DisplayName("Sin identidad la respuesta es 401 aunque el filtro también sea inválido, y no se lee nada")
    void listProfiles_shouldCheckIdentityBeforeStatus() {
        assertThatThrownBy(() -> service.listProfiles(null, "x")).isInstanceOf(IdentityRequiredException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Un estado desconocido se rechaza sin consultar el repositorio")
    void listProfiles_shouldThrowStatusInvalid_whenUnknown() {
        assertThatThrownBy(() -> service.listProfiles("uid-a", "ARCHIVED"))
                .isInstanceOf(ProfileStatusInvalidException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void updateProfileInfo_appliesNameAndSummary() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, "Ana Sofía", "Dev backend", null));
        assertThat(profile.getName().value()).isEqualTo("Ana Sofía");
        assertThat(profile.getSummary().value()).isEqualTo("Dev backend");
        verify(repository).save(profile);
    }

    @Test
    void updateProfileInfo_throwsNotFoundWhenProfileMissing() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfileInfo(
                new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateProfileInfo_nullFieldsDoNotOverwriteExistingValues() {
        var profile = freshProfile();
        profile.updateName(new ProfileName("Nombre original"));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, "nuevo resumen", null));
        assertThat(profile.getName().value()).isEqualTo("Nombre original");
        assertThat(profile.getSummary().value()).isEqualTo("nuevo resumen");
    }

    @Test
    void addWorkExperience_addsEntryAndSaves() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2022-01", null, "CURRENT"));
        assertThat(profile.getWorkExperiences()).hasSize(1);
        assertThat(profile.getWorkExperiences().get(0).getCompany()).isEqualTo("ACME");
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Una experiencia sin empresa, cargo ni estado no se guarda y informa los tres campos")
    void addWorkExperience_shouldThrowFieldErrors_whenCommandHasNullRequiredFields() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));

        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactlyInAnyOrder(
                                tuple("company", ErrorCode.COMPANY_REQUIRED),
                                tuple("position", ErrorCode.POSITION_REQUIRED),
                                tuple("employmentStatus", ErrorCode.EMPLOYMENT_STATUS_REQUIRED)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("El mes actual sale del reloj en UTC: el 31 de octubre a las 23:59:59 octubre se acepta y noviembre no")
    void addWorkExperience_shouldUseUtcClockMonth_whenStartIsCurrentMonth() {
        var lateOctober = new ProfileAppService(repository, roleRepository,
                Clock.fixed(Instant.parse("2026-10-31T23:59:59Z"), ZoneOffset.UTC), replica, events);
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        lateOctober.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2026-10", null, "CURRENT"));

        assertThat(profile.getWorkExperiences()).hasSize(1);
        assertThatThrownBy(() -> lateOctober.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2026-11", null, "CURRENT")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::code).containsExactly(ErrorCode.START_DATE_IN_THE_FUTURE));
        assertThat(profile.getWorkExperiences()).hasSize(1);
    }

    @Test
    @DisplayName("Una fecha de inicio con mes 13 no guarda la experiencia y marca su campo")
    void addWorkExperience_shouldThrowStartDateInvalid_whenStartDateIsMalformed() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2020-13", null, "CURRENT")))
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
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2020-01", null, "FREELANCE")))
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
                UUID.randomUUID(), SVC_UID, "ACME", "Dev", null, "2022-05", "2021-01", "ENDED")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("endDate", ErrorCode.END_DATE_BEFORE_START_DATE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una habilidad sin nombre ni nivel informa los dos campos y no guarda nada")
    void addSkill_shouldThrowFieldErrors_whenCommandHasNullRequiredFields() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addSkill(new AddSkillCommand(UUID.randomUUID(), SVC_UID, null, null)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code, FieldError::message)
                        .containsExactly(
                                tuple("skillName", ErrorCode.SKILL_NAME_REQUIRED, "Ingresa una habilidad."),
                                tuple("level", ErrorCode.SKILL_LEVEL_REQUIRED, "Elige un nivel.")));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una habilidad con nombre de control y nivel desconocido informa los dos campos")
    void addSkill_shouldThrowBothFields_whenNameHasControlAndLevelIsUnknown() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addSkill(new AddSkillCommand(UUID.randomUUID(), SVC_UID, "Py\u0000", "EXPERT")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(
                                tuple("skillName", ErrorCode.SKILL_NAME_INVALID_CHARACTERS),
                                tuple("level", ErrorCode.SKILL_LEVEL_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("La habilidad que se agrega queda con procedencia MANUAL y el nombre recortado")
    void addSkill_shouldSaveManualAndTrimmed_whenNameHasSpaces() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        var saved = service.addSkill(new AddSkillCommand(UUID.randomUUID(), SVC_UID, "  Python ", "BASIC"));
        assertThat(saved.getProfileSkills()).singleElement().satisfies(skill -> {
            assertThat(skill.getSkillName()).isEqualTo("Python");
            assertThat(skill.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        });
    }

    @Test
    @DisplayName("Una educación con inicio AAAA-MM se guarda como MANUAL en ese mes")
    void addEducation_shouldSaveManual_whenStartDateIsValid() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.addEducation(new AddEducationCommand(UUID.randomUUID(), SVC_UID, "Unicauca", "Ingeniería",
                "UNDERGRADUATE", "2018-03", null, false));
        assertThat(profile.getEducations()).singleElement().satisfies(e -> {
            assertThat(e.getStartDate()).isEqualTo(YearMonth.of(2018, 3));
            assertThat(e.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        });
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Un año suelto como inicio no guarda la educación y marca su campo")
    void addEducation_shouldThrowStartDateInvalid_whenStartDateIsOnlyYear() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(UUID.randomUUID(), SVC_UID,
                "Unicauca", "Ingeniería", "UNDERGRADUATE", "2018", null, false)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("startDate", ErrorCode.START_DATE_INVALID_FORMAT)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un nivel de formación que no existe no guarda la educación")
    void addEducation_shouldThrowEducationLevelInvalid_whenLevelIsUnknown() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(UUID.randomUUID(), SVC_UID,
                "Unicauca", "Ingeniería", "DOCTORADO", "2018-01", null, false)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("level", ErrorCode.EDUCATION_LEVEL_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una educación sin institución, título, nivel ni inicio informa los cuatro campos y no guarda")
    void addEducation_shouldThrowFieldErrors_whenCommandHasNullRequiredFields() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(UUID.randomUUID(), SVC_UID,
                null, null, null, null, null, false)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactlyInAnyOrder(
                                tuple("institution", ErrorCode.INSTITUTION_REQUIRED),
                                tuple("degree", ErrorCode.DEGREE_REQUIRED),
                                tuple("level", ErrorCode.EDUCATION_LEVEL_REQUIRED),
                                tuple("startDate", ErrorCode.START_DATE_REQUIRED)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un resumen con solo espacios borra el resumen del perfil en Borrador (CA-2.3.4)")
    void updateProfileInfo_shouldClearSummary_whenSummaryIsBlank() {
        var profile = freshProfile();
        profile.updateSummary(new ProfessionalSummary("Resumen anterior"));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, "   ", null));

        assertThat(profile.getSummary()).isNull();
        verify(repository).save(profile);
    }

    @ParameterizedTest(name = "resumen {0}")
    @ValueSource(strings = {"\u00A0", "\uFEFF", "\u3000"})
    @DisplayName("Un resumen de solo espacios que Java no reconoce como en blanco también borra el resumen, sin error")
    void updateProfileInfo_shouldClearSummary_whenSummaryIsOnlyNoBreakSpace(String blank) {
        var profile = freshProfile();
        profile.updateSummary(new ProfessionalSummary("Resumen anterior"));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, null, blank, null));

        assertThat(profile.getSummary()).isNull();
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Una modalidad de la lista se guarda en el perfil y la procedencia del perfil no cambia")
    void updateProfileInfo_shouldApplyModalityAndKeepProvenance_whenModalityIsAnOption() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), SVC_UID, null, null, "REMOTE"));
        assertThat(profile.getPreferredModality()).isEqualTo(WorkModality.REMOTE);
        assertThat(profile.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("El comando no lleva procedencia: un perfil MANUAL sigue MANUAL tras editar la información general")
    void updateProfileInfo_shouldIgnoreProvenance_whenCommandHasNone() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, "Ana", "Resumen", "HYBRID"));

        assertThat(profile.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        assertThat(UpdateProfileInfoCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("provenance");
    }

    @Test
    @DisplayName("Un nombre vacío, un resumen demasiado largo y una modalidad inválida se informan a la vez y no guardan nada")
    void updateProfileInfo_shouldReportAllFields_whenSeveralAreInvalid() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));

        assertThatThrownBy(() -> service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), SVC_UID, "", "a".repeat(2001), "PRESENCIAL")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactlyInAnyOrder(
                                tuple("name", ErrorCode.PROFILE_NAME_REQUIRED),
                                tuple("summary", ErrorCode.SUMMARY_TOO_LONG),
                                tuple("preferredModality", ErrorCode.PREFERRED_MODALITY_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un carácter de control en el nombre o el resumen se rechaza con su código y no guarda nada")
    void updateProfileInfo_shouldRejectControlCharacters_whenNameAndSummaryHaveThem() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));

        assertThatThrownBy(() -> service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), SVC_UID, "Analista\0", "Hola\0", null)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::code)
                        .containsExactlyInAnyOrder(ErrorCode.PROFILE_NAME_INVALID_CHARACTERS,
                                ErrorCode.SUMMARY_INVALID_CHARACTERS));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Vaciar el resumen de un perfil activo se rechaza, no guarda nada y conserva el resumen y el estado")
    void updateProfileInfo_shouldNotSave_whenSummaryClearIsRejected() {
        var profile = completeProfile();
        profile.complete();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), COMPLETE_UID, null, "", null)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("summary", ErrorCode.SUMMARY_NOT_ALLOWED)));

        assertThat(profile.getSummary()).isNotNull();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Renombrar un perfil activo lo guarda y lo deja activo")
    void updateProfileInfo_shouldRenameAndKeepActive_whenProfileIsCompleted() {
        var profile = completeProfile();
        profile.complete();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.updateProfileInfo(new UpdateProfileInfoCommand(
                UUID.randomUUID(), COMPLETE_UID, "  Analista de datos senior ", null, null));

        assertThat(profile.getName().value()).isEqualTo("Analista de datos senior");
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("La edición bloquea la fila del perfil y no usa la lectura sin bloqueo")
    void updateProfileInfo_shouldLoadForUpdate_whenCalled() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));

        service.updateProfileInfo(new UpdateProfileInfoCommand(UUID.randomUUID(), SVC_UID, "Ana", null, null));

        verify(repository).findByIdForUpdate(any());
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("Editar un rol objetivo del perfil lo cambia por el rol del catálogo")
    void updateTargetRole_shouldReplaceRole_whenRoleIsInProfile() {
        var profile = freshProfile();
        profile.addTargetRole(new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Backend", DataProvenance.MANUAL));
        var roleId = profile.getTargetRoles().get(0).getId();
        var catalogRole = new ProfessionalRole(UUID.randomUUID(), "Frontend Developer", "Desarrollo", true);
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
        var catalogRole = new ProfessionalRole(UUID.randomUUID(), "Frontend Developer", "Desarrollo", true);
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
                UUID.randomUUID(), SVC_UID, null, null, "PRESENCIAL")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("preferredModality", ErrorCode.PREFERRED_MODALITY_INVALID_VALUE)));
        verify(repository, never()).save(any());
    }

    @Test
    void removeWorkExperience_removesFromProfileAndSaves() {
        var profile = freshProfile();
        profile.addWorkExperience(co.edu.unicauca.cameia.perfil.domain.model.WorkExperience.rebuild(
                UUID.randomUUID(), new co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft("ACME", "Dev", null,
                        YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT),
                DataProvenance.MANUAL));
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
        service.addSkill(new AddSkillCommand(UUID.randomUUID(), SVC_UID, "Java", "ADVANCED"));
        assertThat(profile.getProfileSkills()).hasSize(1);
        assertThat(profile.getProfileSkills().get(0).getSkillName()).isEqualTo("Java");
        verify(repository).save(profile);
    }

    @Test
    void removeSkill_removesSkillAndSaves() {
        var profile = freshProfile();
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));
        var skillId = profile.getProfileSkills().get(0).getId();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.removeSkill(UUID.randomUUID(), SVC_UID, skillId);
        assertThat(profile.getProfileSkills()).isEmpty();
        verify(repository).save(profile);
    }

    @Test
    void addTargetRole_addsRoleAndSaves() {
        var profile = freshProfile();
        var roleId = UUID.randomUUID();
        var catRole = new ProfessionalRole(roleId, "Backend Developer", "Desarrollo", true);
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(catRole));
        service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), SVC_UID, roleId));
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

    /** Los 12 casos de uso que modifican un perfil, cada uno con una llamada que no necesita datos válidos. */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> writeUseCases() {
        var id = UUID.randomUUID();
        return java.util.stream.Stream.of(
                use("updateProfileInfo", s -> s.updateProfileInfo(new UpdateProfileInfoCommand(id, SVC_UID, null, null, null))),
                use("addWorkExperience", s -> s.addWorkExperience(new AddWorkExperienceCommand(
                        id, SVC_UID, "ACME", "Dev", null, "2022-01", null, "CURRENT"))),
                use("removeWorkExperience", s -> s.removeWorkExperience(id, SVC_UID, UUID.randomUUID())),
                use("addEducation", s -> s.addEducation(new AddEducationCommand(id, SVC_UID, "Unicauca", "Ingeniería",
                        "UNDERGRADUATE", "2018-01", null, false))),
                use("removeEducation", s -> s.removeEducation(id, SVC_UID, UUID.randomUUID())),
                use("updateSalaryExpectation", s -> s.updateSalaryExpectation(
                        new UpdateSalaryExpectationCommand(id, SVC_UID, new BigDecimal("3500000")))),
                use("addSkill", s -> s.addSkill(new AddSkillCommand(id, SVC_UID, "Java", "ADVANCED"))),
                use("removeSkill", s -> s.removeSkill(id, SVC_UID, UUID.randomUUID())),
                use("addTargetRole", s -> s.addTargetRole(new AddTargetRoleCommand(id, SVC_UID, UUID.randomUUID()))),
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

    // ── roles objetivo: solo roles activos, procedencia MANUAL ────────────

    private static final ProfessionalRole ACTIVE_ROLE = new ProfessionalRole(
            UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Desarrollador Backend", "Desarrollo", true);
    private static final ProfessionalRole INACTIVE_ROLE = new ProfessionalRole(
            UUID.fromString("a0000007-0000-0000-0000-000000000006"), "Desarrollador Blockchain", "Otros TI", false);

    @org.junit.jupiter.params.ParameterizedTest(name = "perfil {0}")
    @org.junit.jupiter.params.provider.EnumSource(value = ProfileStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    @DisplayName("Agregar un rol activo guarda la procedencia MANUAL, con el perfil en curso o finalizado")
    void addTargetRole_shouldStoreManualProvenance_whenRoleIsActive(ProfileStatus status) {
        var profile = status == ProfileStatus.COMPLETED ? completeProfile() : freshProfile();
        var uid = profile.getFirebaseUid().value();
        if (status == ProfileStatus.COMPLETED) profile.complete();
        var before = profile.getTargetRoles().size();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(ACTIVE_ROLE.id())).thenReturn(Optional.of(ACTIVE_ROLE));

        service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), uid, ACTIVE_ROLE.id()));

        assertThat(profile.getTargetRoles()).hasSize(before + 1);
        var added = profile.getTargetRoles().getLast();
        assertThat(added.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        assertThat(added.getRoleTitle()).isEqualTo("Desarrollador Backend");
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Agregar un rol inactivo se rechaza en professionalRoleId y no guarda nada")
    void addTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(INACTIVE_ROLE.id())).thenReturn(Optional.of(INACTIVE_ROLE));

        assertThatThrownBy(() -> service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), SVC_UID, INACTIVE_ROLE.id())))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("professionalRoleId", ErrorCode.PROFESSIONAL_ROLE_DISABLED)));
        verify(repository, never()).save(any());
        assertThat(profile.getTargetRoles()).isEmpty();
    }

    @Test
    @DisplayName("Con 5 roles, un rol inactivo se rechaza como inactivo y no como máximo alcanzado")
    void addTargetRole_shouldRejectInactiveRole_beforeLimit_whenProfileIsFull() {
        var profile = fullProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(INACTIVE_ROLE.id())).thenReturn(Optional.of(INACTIVE_ROLE));

        assertThatThrownBy(() -> service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), SVC_UID, INACTIVE_ROLE.id())))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("professionalRoleId", ErrorCode.PROFESSIONAL_ROLE_DISABLED)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Con 5 roles, un rol activo nuevo responde máximo alcanzado y no guarda")
    void addTargetRole_shouldThrowLimit_whenProfileIsFull() {
        var profile = fullProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(ACTIVE_ROLE.id())).thenReturn(Optional.of(ACTIVE_ROLE));

        assertThatThrownBy(() -> service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), SVC_UID, ACTIVE_ROLE.id())))
                .isInstanceOf(MaxTargetRolesExceededException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un rol que no está en el catálogo responde 404 y no guarda")
    void addTargetRole_shouldThrowNotFound_whenRoleIsNotInCatalog() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));
        when(roleRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addTargetRole(new AddTargetRoleCommand(UUID.randomUUID(), SVC_UID, UUID.randomUUID())))
                .isInstanceOf(ProfessionalRoleNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sustituir por un rol inactivo se rechaza y el perfil conserva su rol")
    void updateTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled() {
        var profile = freshProfile();
        var original = new TargetRole(UUID.randomUUID(), ACTIVE_ROLE.id(), "Desarrollador Backend", DataProvenance.MANUAL);
        profile.addTargetRole(original);
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(INACTIVE_ROLE.id())).thenReturn(Optional.of(INACTIVE_ROLE));

        assertThatThrownBy(() -> service.updateTargetRole(
                new UpdateTargetRoleCommand(UUID.randomUUID(), SVC_UID, original.getId(), INACTIVE_ROLE.id())))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("professionalRoleId", ErrorCode.PROFESSIONAL_ROLE_DISABLED)));
        assertThat(profile.getTargetRoles()).singleElement()
                .satisfies(r -> assertThat(r.getProfessionalRoleId()).isEqualTo(ACTIVE_ROLE.id()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sustituir un rol objetivo que no está en el perfil por un rol inactivo informa primero el error del catálogo")
    void updateTargetRole_shouldReportCatalogRole_whenRoleIdIsNotInProfile() {
        var profile = freshProfile();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(INACTIVE_ROLE.id())).thenReturn(Optional.of(INACTIVE_ROLE));

        assertThatThrownBy(() -> service.updateTargetRole(
                new UpdateTargetRoleCommand(UUID.randomUUID(), SVC_UID, UUID.randomUUID(), INACTIVE_ROLE.id())))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("professionalRoleId", ErrorCode.PROFESSIONAL_ROLE_DISABLED)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sustituir conserva el id y la procedencia del rol y cambia el rol del catálogo")
    void updateTargetRole_shouldKeepIdAndProvenance_whenRoleIsReplaced() {
        var profile = freshProfile();
        var original = new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Otro rol", DataProvenance.AI_SUGGESTED);
        profile.addTargetRole(original);
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        when(roleRepository.findById(ACTIVE_ROLE.id())).thenReturn(Optional.of(ACTIVE_ROLE));

        service.updateTargetRole(new UpdateTargetRoleCommand(UUID.randomUUID(), SVC_UID, original.getId(), ACTIVE_ROLE.id()));

        assertThat(profile.getTargetRoles()).singleElement().satisfies(r -> {
            assertThat(r.getId()).isEqualTo(original.getId());
            assertThat(r.getProvenance()).isEqualTo(DataProvenance.AI_SUGGESTED);
            assertThat(r.getProfessionalRoleId()).isEqualTo(ACTIVE_ROLE.id());
            assertThat(r.getRoleTitle()).isEqualTo("Desarrollador Backend");
        });
        verify(repository).save(profile);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("targetRoleWrites")
    @DisplayName("Una escritura de roles objetivo sobre un perfil ajeno responde 403 sin guardar ni consultar el catálogo")
    void targetRoleWrites_shouldThrowAccessDenied_whenProfileBelongsToOtherUser(String name,
            java.util.function.Consumer<ProfileAppService> call) {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshProfile()));

        assertThatThrownBy(() -> call.accept(service)).isInstanceOf(ProfileAccessDeniedException.class);

        verify(repository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(roleRepository);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> targetRoleWrites() {
        var id = UUID.randomUUID();
        return java.util.stream.Stream.of(
                use("addTargetRole", s -> s.addTargetRole(new AddTargetRoleCommand(id, "uid-other", UUID.randomUUID()))),
                use("updateTargetRole", s -> s.updateTargetRole(
                        new UpdateTargetRoleCommand(id, "uid-other", UUID.randomUUID(), UUID.randomUUID()))),
                use("removeTargetRole", s -> s.removeTargetRole(id, "uid-other", UUID.randomUUID())));
    }

    @Test
    @DisplayName("Un perfil en Borrador puede quedarse sin roles objetivo")
    void removeTargetRole_shouldRemoveLastRole_whenProfileIsDraft() {
        var profile = freshProfile();
        var role = new TargetRole(UUID.randomUUID(), ACTIVE_ROLE.id(), "Desarrollador Backend", DataProvenance.MANUAL);
        profile.addTargetRole(role);
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.removeTargetRole(UUID.randomUUID(), SVC_UID, role.getId());

        assertThat(profile.getTargetRoles()).isEmpty();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("El único rol de un perfil activo no se elimina: el perfil conserva el rol y el estado")
    void removeTargetRole_shouldKeepRoleAndStatus_whenLastRoleOfCompletedProfile() {
        var profile = completeProfile();
        profile.complete();
        var roleId = profile.getTargetRoles().get(0).getId();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.removeTargetRole(UUID.randomUUID(), COMPLETE_UID, roleId))
                .isInstanceOf(LastTargetRoleException.class);

        assertThat(profile.getTargetRoles()).hasSize(1);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un perfil activo con dos roles puede quitar uno y sigue activo")
    void removeTargetRole_shouldRemoveRole_whenCompletedProfileHasTwo() {
        var profile = completeProfile();
        var second = new TargetRole(UUID.randomUUID(), ACTIVE_ROLE.id(), "Desarrollador Backend", DataProvenance.MANUAL);
        profile.addTargetRole(second);
        profile.complete();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.removeTargetRole(UUID.randomUUID(), COMPLETE_UID, second.getId());

        assertThat(profile.getTargetRoles()).hasSize(1);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Repetir la eliminación de un rol ya eliminado responde que no existe")
    void removeTargetRole_shouldThrowNotFound_whenRoleWasAlreadyRemoved() {
        var profile = freshProfile();
        var role = new TargetRole(UUID.randomUUID(), ACTIVE_ROLE.id(), "Desarrollador Backend", DataProvenance.MANUAL);
        profile.addTargetRole(role);
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        service.removeTargetRole(UUID.randomUUID(), SVC_UID, role.getId());

        assertThatThrownBy(() -> service.removeTargetRole(UUID.randomUUID(), SVC_UID, role.getId()))
                .isInstanceOf(TargetRoleNotFoundException.class);
    }

    private static ProfessionalProfile fullProfile() {
        var profile = freshProfile();
        for (int i = 0; i < 5; i++) {
            profile.addTargetRole(new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Rol " + i, DataProvenance.MANUAL));
        }
        return profile;
    }

    // ---- Fecha de nacimiento (réplica local)

    /** Usuario sin fila en la réplica. */
    static final String NO_BIRTH_UID = "firebase-sin-nacimiento";
    /** Usuaria nacida el 15 de marzo de 2008: cumple 15 años en marzo de 2023. */
    static final String BORN_UID = "firebase-nacida-2008";

    @Test
    @DisplayName("Un registro sin ninguna fecha no lee la réplica y se guarda aunque no haya fecha de nacimiento")
    void addWorkExperience_shouldNotReadReplica_whenRecordHasNoDates() {
        var profile = ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), NO_BIRTH_UID, "ACME", "Dev", null, null, null, "UNKNOWN_END"));

        assertThat(profile.getWorkExperiences()).hasSize(1);
        verify(replica, never()).findBirthDate(any());
        verify(repository).save(profile);
    }

    @Test
    @DisplayName("Con fechas y sin fecha de nacimiento replicada responde 503 y no guarda")
    void addWorkExperience_shouldThrowBirthDateUnavailable_whenReplicaIsEmptyAndRecordHasDates() {
        var profile = ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), NO_BIRTH_UID, "ACME", "Dev", null, "2020-01", null, "CURRENT")))
                .isInstanceOf(BirthDateUnavailableException.class);

        assertThat(profile.getWorkExperiences()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una experiencia solo con fecha de fin también necesita la fecha de nacimiento")
    void addWorkExperience_shouldThrowBirthDateUnavailable_whenOnlyEndDateIsPresent() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID))));

        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), NO_BIRTH_UID, "ACME", "Dev", null, null, "2020-05", "ENDED")))
                .isInstanceOf(BirthDateUnavailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una formación sin fecha de nacimiento replicada responde 503 y no guarda")
    void addEducation_shouldThrowBirthDateUnavailable_whenReplicaIsEmpty() {
        var profile = ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(
                UUID.randomUUID(), NO_BIRTH_UID, "Unicauca", "Ingeniería", "UNDERGRADUATE", "2018-01", null, true)))
                .isInstanceOf(BirthDateUnavailableException.class);

        assertThat(profile.getEducations()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Las reglas del registro se informan antes que la falta de la fecha de nacimiento (422 antes que 503)")
    void addWorkExperience_shouldReportFieldErrors_whenRecordIsInvalidAndReplicaIsEmpty() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID))));

        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), NO_BIRTH_UID, "", "Dev", null, "2020-01", null, "CURRENT")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("company", ErrorCode.COMPANY_REQUIRED)));
    }

    @Test
    @DisplayName("Una formación con solo fecha de fin y sin réplica responde el inicio requerido (422) y no el 503")
    void addEducation_shouldReportStartRequired_whenOnlyEndDateIsPresentAndReplicaIsEmpty() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID))));

        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(
                UUID.randomUUID(), NO_BIRTH_UID, "Unicauca", "Ingeniería", "UNDERGRADUATE", null, "2022-12", false)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("startDate", ErrorCode.START_DATE_REQUIRED)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("La réplica se lee una sola vez por petición")
    void addWorkExperience_shouldReadReplicaOnce_whenRecordHasDates() {
        replica.saveIfAbsent(new FirebaseUid(BORN_UID), LocalDate.of(2008, 3, 15));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(BORN_UID))));

        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), BORN_UID, "ACME", "Dev", null, "2023-03", "2024-01", "ENDED"));

        verify(replica, times(1)).findBirthDate(new FirebaseUid(BORN_UID));
    }

    @Test
    @DisplayName("Un inicio antes del mes en que se cumplen 15 años se rechaza y el mes de los 15 años se acepta")
    void addWorkExperience_shouldApplyMinimumAge_whenBirthDateIsReplicated() {
        replica.saveIfAbsent(new FirebaseUid(BORN_UID), LocalDate.of(2008, 3, 15));
        var profile = ProfessionalProfile.create(new FirebaseUid(BORN_UID));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), BORN_UID, "ACME", "Dev", null, "2023-02", null, "CURRENT")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("startDate", ErrorCode.START_DATE_BEFORE_MINIMUM_AGE)));
        service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), BORN_UID, "ACME", "Dev", null, "2023-03", null, "CURRENT"));

        assertThat(profile.getWorkExperiences()).hasSize(1);
    }

    @Test
    @DisplayName("Una regla de nacimiento y otra del registro se informan a la vez")
    void addWorkExperience_shouldReportEveryField_whenBirthRuleAndAnotherRuleFail() {
        replica.saveIfAbsent(new FirebaseUid(BORN_UID), LocalDate.of(2008, 3, 15));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(BORN_UID))));

        assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                UUID.randomUUID(), BORN_UID, "", "Dev", null, "2023-02", null, "CURRENT")))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactlyInAnyOrder(tuple("company", ErrorCode.COMPANY_REQUIRED),
                                tuple("startDate", ErrorCode.START_DATE_BEFORE_MINIMUM_AGE)));
    }

    @Test
    @DisplayName("Una formación que empieza antes del mes de nacimiento se rechaza y la del mes de nacimiento se acepta")
    void addEducation_shouldRejectStartBeforeBirth_whenBirthDateIsReplicated() {
        replica.saveIfAbsent(new FirebaseUid(BORN_UID), LocalDate.of(2008, 3, 15));
        var profile = ProfessionalProfile.create(new FirebaseUid(BORN_UID));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addEducation(new AddEducationCommand(
                UUID.randomUUID(), BORN_UID, "Unicauca", "Curso", "TECHNICAL", "2008-02", null, true)))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldError::field, FieldError::code)
                        .containsExactly(tuple("startDate", ErrorCode.START_DATE_BEFORE_BIRTH)));
        service.addEducation(new AddEducationCommand(
                UUID.randomUUID(), BORN_UID, "Unicauca", "Curso", "TECHNICAL", "2008-03", null, true));

        assertThat(profile.getEducations()).hasSize(1);
    }

    @Test
    @DisplayName("La fecha de nacimiento no aparece en ningún log, ni al guardar ni al responder 503")
    void addWorkExperience_shouldNeverLogBirthDate_whenReplicaIsRead() {
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        try {
            replica.saveIfAbsent(new FirebaseUid(BORN_UID), LocalDate.of(2008, 3, 15));
            when(repository.findByIdForUpdate(any()))
                    .thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(BORN_UID))))
                    .thenReturn(Optional.of(ProfessionalProfile.create(new FirebaseUid(NO_BIRTH_UID))));

            service.addWorkExperience(new AddWorkExperienceCommand(
                    UUID.randomUUID(), BORN_UID, "ACME", "Dev", null, "2023-03", null, "CURRENT"));
            assertThatThrownBy(() -> service.addWorkExperience(new AddWorkExperienceCommand(
                    UUID.randomUUID(), NO_BIRTH_UID, "ACME", "Dev", null, "2023-03", null, "CURRENT")))
                    .isInstanceOf(BirthDateUnavailableException.class);
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("2008"));
    }

    // ── anuncio del evento del perfil ──────────────────────────────────────

    /** Perfil en Borrador con nombre, resumen, una experiencia, una formación, una habilidad y un rol, para las once escrituras. */
    private ProfessionalProfile draftWithOneOfEverything() {
        var profile = freshProfile();
        profile.updateName(new ProfileName("Analista de datos"));
        profile.updateSummary(new ProfessionalSummary("Analista con tres años de experiencia en SQL y Python."));
        var bounds = new DateBounds(YearMonth.of(2026, 10), YearMonth.of(1990, 1));
        profile.addWorkExperience(WorkExperience.create(new WorkExperienceDraft("ACME", "Analista", null,
                YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT), bounds));
        profile.addEducation(Education.create(new EducationDraft("Universidad X", "Ingeniería",
                EducationLevel.UNDERGRADUATE, YearMonth.of(2018, 1), null, false), bounds));
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));
        profile.addTargetRole(new TargetRole(UUID.randomUUID(),
                UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Desarrollador Backend", DataProvenance.MANUAL));
        return profile;
    }

    private void stubLockedProfileAndCatalog(ProfessionalProfile profile) {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile));
        lenient().when(roleRepository.findById(any())).thenReturn(Optional.of(new ProfessionalRole(
                UUID.fromString("a0000001-0000-0000-0000-000000000003"), "Data Engineer", "Datos", true)));
    }

    /** Las once escrituras que Entrevista debe ver; cada una recibe el servicio y el perfil sobre el que se prueba. */
    static Stream<Arguments> successfulWrites() {
        var id = UUID.randomUUID();
        var otherRole = UUID.fromString("a0000001-0000-0000-0000-000000000003");
        return Stream.of(
                write("updateProfileInfo", (s, p) -> s.updateProfileInfo(
                        new UpdateProfileInfoCommand(id, SVC_UID, "Analista senior", null, null))),
                write("addWorkExperience", (s, p) -> s.addWorkExperience(new AddWorkExperienceCommand(
                        id, SVC_UID, "Globex", "QA", null, "2023-01", null, "CURRENT"))),
                write("removeWorkExperience", (s, p) -> s.removeWorkExperience(id, SVC_UID, p.getWorkExperiences().get(0).getId())),
                write("addEducation", (s, p) -> s.addEducation(new AddEducationCommand(id, SVC_UID, "Unicauca", "Maestría",
                        "POSTGRADUATE", "2024-01", null, true))),
                write("removeEducation", (s, p) -> s.removeEducation(id, SVC_UID, p.getEducations().get(0).getId())),
                write("addSkill", (s, p) -> s.addSkill(new AddSkillCommand(id, SVC_UID, "SQL", "BASIC"))),
                write("removeSkill", (s, p) -> s.removeSkill(id, SVC_UID, p.getProfileSkills().get(0).getId())),
                write("addTargetRole", (s, p) -> s.addTargetRole(new AddTargetRoleCommand(id, SVC_UID, otherRole))),
                write("updateTargetRole", (s, p) -> s.updateTargetRole(
                        new UpdateTargetRoleCommand(id, SVC_UID, p.getTargetRoles().get(0).getId(), otherRole))),
                write("removeTargetRole", (s, p) -> s.removeTargetRole(id, SVC_UID, p.getTargetRoles().get(0).getId())),
                write("completeProfile", (s, p) -> s.completeProfile(id, SVC_UID)));
    }

    private static Arguments write(String name, BiConsumer<ProfileAppService, ProfessionalProfile> call) {
        return Arguments.of(name, call);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulWrites")
    @DisplayName("Cada una de las once escrituras guarda el perfil y después lo anuncia, una sola vez")
    void writes_shouldAnnounceAfterSave_whenWriteSucceeds(String name, BiConsumer<ProfileAppService, ProfessionalProfile> call) {
        var profile = draftWithOneOfEverything();
        stubLockedProfileAndCatalog(profile);

        call.accept(service, profile);

        var order = inOrder(repository, events);
        order.verify(repository).save(profile);
        order.verify(events).announceIfCompleted(profile);
        verify(events, times(1)).announceIfCompleted(profile);
    }

    @Test
    @DisplayName("La expectativa salarial no viaja en el evento: guardar basta")
    void updateSalaryExpectation_shouldNotAnnounce_whenSaved() {
        var profile = draftWithOneOfEverything();
        stubLockedProfileAndCatalog(profile);

        service.updateSalaryExpectation(new UpdateSalaryExpectationCommand(UUID.randomUUID(), SVC_UID, new BigDecimal("3500000")));

        verify(repository).save(profile);
        verify(events, never()).announceIfCompleted(any());
    }

    static Stream<Arguments> rejectedWrites() {
        var id = UUID.randomUUID();
        return Stream.of(
                Arguments.of("habilidad en blanco", false,
                        (Consumer<ProfileAppService>) s -> s.addSkill(new AddSkillCommand(id, SVC_UID, "   ", "BASIC"))),
                Arguments.of("finalizar un borrador incompleto", true,
                        (Consumer<ProfileAppService>) s -> s.completeProfile(id, SVC_UID)),
                Arguments.of("perfil ajeno", false,
                        (Consumer<ProfileAppService>) s -> s.addSkill(new AddSkillCommand(id, "otro-uid", "Go", "BASIC"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedWrites")
    @DisplayName("Una escritura rechazada no guarda ni anuncia nada")
    void writes_shouldNotAnnounce_whenWriteIsRejected(String name, boolean freshDraft, Consumer<ProfileAppService> call) {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(freshDraft ? freshProfile() : draftWithOneOfEverything()));

        assertThatThrownBy(() -> call.accept(service)).isInstanceOf(RuntimeException.class);

        verify(events, never()).announceIfCompleted(any());
        verify(repository, never()).save(any());
    }

    private static ProfessionalProfile freshProfile() {
        return ProfessionalProfile.create(new FirebaseUid(SVC_UID));
    }

    private static ProfessionalProfile completeProfile() {
        var p = ProfessionalProfile.create(new FirebaseUid(COMPLETE_UID));
        p.updateName(new ProfileName("Ana Sofía"));
        p.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Java y DDD."));
        p.addTargetRole(new TargetRole(UUID.randomUUID(), UUID.randomUUID(), "Backend Developer", DataProvenance.MANUAL));
        p.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));
        p.addEducation(Education.create(new EducationDraft("Universidad X", "Ingeniería",
                EducationLevel.UNDERGRADUATE, YearMonth.of(2018, 1), null, false),
                new DateBounds(YearMonth.of(2026, 10), null)));
        return p;
    }
}
