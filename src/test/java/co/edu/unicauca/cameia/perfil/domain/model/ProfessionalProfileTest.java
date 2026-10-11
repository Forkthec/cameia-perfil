package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateSkillException;
import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.EducationNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotAllowedException;
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalProfileTest {

    private static final FirebaseUid UID = new FirebaseUid("firebase-test-uid-001");

    // ── create() ─────────────────────────────────────────────────────────

    @Test
    void create_producesInProgressProfileWithEmptyCollections() {
        var profile = ProfessionalProfile.create(UID);

        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getFirebaseUid()).isEqualTo(UID);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
        assertThat(profile.getReviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(profile.getProvenance()).isEqualTo(DataProvenance.MANUAL);
        assertThat(profile.getName()).isNull();
        assertThat(profile.getSummary()).isNull();
        assertThat(profile.getTargetRoles()).isEmpty();
        assertThat(profile.getWorkExperiences()).isEmpty();
        assertThat(profile.getEducations()).isEmpty();
        assertThat(profile.getProfileSkills()).isEmpty();
        assertThat(profile.isComplete()).isFalse();
    }

    // ── isComplete() / getMissingRequirements() ───────────────────────────

    @Test
    void isComplete_requiresAllFiveConditions() {
        var profile = ProfessionalProfile.create(UID);
        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.getMissingRequirements()).hasSize(5);

        profile.updateName(new ProfileName("Ana Sofía"));
        assertThat(profile.isComplete()).isFalse();

        profile.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Spring Boot."));
        assertThat(profile.isComplete()).isFalse();

        profile.addTargetRole(role(UUID.randomUUID(), "Backend Dev"));
        assertThat(profile.isComplete()).isFalse();

        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));
        assertThat(profile.isComplete()).isFalse();

        profile.addEducation(edu());
        assertThat(profile.isComplete()).isTrue();
    }

    // ── addTargetRole() ───────────────────────────────────────────────────

    @Test
    void addTargetRole_throwsMaxExceededWhenLimitReached() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < ProfessionalProfile.MAX_TARGET_ROLES; i++) {
            profile.addTargetRole(role(UUID.randomUUID(), "Role " + i));
        }

        assertThatThrownBy(() -> profile.addTargetRole(role(UUID.randomUUID(), "Extra")))
                .isInstanceOf(MaxTargetRolesExceededException.class);
    }

    @Test
    @DisplayName("Con 4 roles todavía se acepta el quinto")
    void addTargetRole_shouldAccept_whenProfileHasFourRoles() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < ProfessionalProfile.MAX_TARGET_ROLES - 1; i++) {
            profile.addTargetRole(role(UUID.randomUUID(), "Role " + i));
        }

        profile.addTargetRole(role(UUID.randomUUID(), "Quinto"));

        assertThat(profile.getTargetRoles()).hasSize(ProfessionalProfile.MAX_TARGET_ROLES);
    }

    @Test
    @DisplayName("Con 5 roles, repetir uno de ellos informa el máximo y no el duplicado")
    void addTargetRole_shouldReportLimit_whenFullAndRepeated() {
        var repeated = UUID.randomUUID();
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(role(repeated, "Repetido"));
        for (int i = 1; i < ProfessionalProfile.MAX_TARGET_ROLES; i++) {
            profile.addTargetRole(role(UUID.randomUUID(), "Role " + i));
        }

        assertThatThrownBy(() -> profile.addTargetRole(role(repeated, "Repetido")))
                .isInstanceOf(MaxTargetRolesExceededException.class);
    }

    @Test
    @DisplayName("Sustituir un rol por el mismo rol del catálogo no es un duplicado")
    void updateTargetRole_shouldKeepRole_whenReplacedBySameCatalogRole() {
        var catalogId = UUID.randomUUID();
        var existing = role(catalogId, "Backend Dev");
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(existing);

        profile.updateTargetRole(existing.getId(), catalogId, "Backend Dev");

        assertThat(profile.getTargetRoles()).singleElement()
                .satisfies(r -> assertThat(r.getId()).isEqualTo(existing.getId()));
    }

    @Test
    @DisplayName("Sustituir un rol deja el nuevo en la misma posición y con el mismo id")
    void updateTargetRole_shouldKeepPosition_whenRoleIsReplaced() {
        var a = UUID.fromString("a0000001-0000-0000-0000-000000000001");
        var b = UUID.fromString("a0000001-0000-0000-0000-000000000002");
        var c = UUID.fromString("a0000001-0000-0000-0000-000000000003");
        var d = UUID.fromString("a0000001-0000-0000-0000-000000000004");
        var profile = ProfessionalProfile.create(UID);
        var second = role(b, "Rol B");
        profile.addTargetRole(role(a, "Rol A"));
        profile.addTargetRole(second);
        profile.addTargetRole(role(c, "Rol C"));

        profile.updateTargetRole(second.getId(), d, "Rol D");

        assertThat(profile.getTargetRoles()).extracting(TargetRole::getProfessionalRoleId).containsExactly(a, d, c);
        assertThat(profile.getTargetRoles().get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("Sustituir sin rol ni título nuevos conserva los valores anteriores")
    void updateTargetRole_shouldKeepPreviousValues_whenArgumentsAreNull() {
        var roleId = UUID.fromString("a0000001-0000-0000-0000-000000000002");
        var profile = ProfessionalProfile.create(UID);
        var existing = role(roleId, "Backend Dev");
        profile.addTargetRole(existing);

        profile.updateTargetRole(existing.getId(), null, null);

        assertThat(profile.getTargetRoles()).singleElement().satisfies(r -> {
            assertThat(r.getProfessionalRoleId()).isEqualTo(roleId);
            assertThat(r.getRoleTitle()).isEqualTo("Backend Dev");
        });
    }

    @Test
    @DisplayName("Quitar un rol no cambia el orden de los demás")
    void removeTargetRole_shouldKeepOrderOfOthers_whenOneIsRemoved() {
        var a = role(UUID.fromString("a0000001-0000-0000-0000-000000000001"), "Rol A");
        var b = role(UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Rol B");
        var c = role(UUID.fromString("a0000001-0000-0000-0000-000000000003"), "Rol C");
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(a);
        profile.addTargetRole(b);
        profile.addTargetRole(c);

        profile.removeTargetRole(a.getId());

        assertThat(profile.getTargetRoles()).extracting(TargetRole::getId).containsExactly(b.getId(), c.getId());
    }

    @Test
    @DisplayName("Eliminar de un perfil sin roles responde que el rol no existe")
    void removeTargetRole_shouldThrowNotFound_whenProfileHasNoRoles() {
        var profile = ProfessionalProfile.create(UID);

        assertThatThrownBy(() -> profile.removeTargetRole(UUID.fromString("33333333-3333-4333-8333-333333333333")))
                .isInstanceOf(TargetRoleNotFoundException.class);
    }

    @Test
    void addTargetRole_throwsDuplicateForSameProfessionalRoleId() {
        var roleId = UUID.randomUUID();
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(role(roleId, "Backend Dev"));

        assertThatThrownBy(() -> profile.addTargetRole(role(roleId, "Backend Dev")))
                .isInstanceOf(DuplicateTargetRoleException.class);
    }

    @Test
    void addTargetRole_allowsDifferentProfessionalRoleIds() {
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(role(UUID.randomUUID(), "Backend Dev"));

        assertThatCode(() -> profile.addTargetRole(role(UUID.randomUUID(), "Frontend Dev")))
                .doesNotThrowAnyException();
        assertThat(profile.getTargetRoles()).hasSize(2);
    }

    // ── removeTargetRole() ────────────────────────────────────────────────

    @Test
    void removeTargetRole_throwsLastTargetRoleWhenOnlyOneRemainsOnCompletedProfile() {
        var profile = buildCompleteProfile();
        profile.complete();
        var onlyRole = profile.getTargetRoles().get(0);

        assertThatThrownBy(() -> profile.removeTargetRole(onlyRole.getId()))
                .isInstanceOf(LastTargetRoleException.class);
    }

    @Test
    void removeTargetRole_removesCorrectlyWhenMultipleExist() {
        var profile = ProfessionalProfile.create(UID);
        var first = role(UUID.randomUUID(), "Role A");
        var second = role(UUID.randomUUID(), "Role B");
        profile.addTargetRole(first);
        profile.addTargetRole(second);

        profile.removeTargetRole(first.getId());

        assertThat(profile.getTargetRoles()).hasSize(1);
        assertThat(profile.getTargetRoles().get(0).getId()).isEqualTo(second.getId());
    }

    // ── complete() ────────────────────────────────────────────────────────

    @Test
    void complete_setsStatusToCompleted() {
        var profile = buildCompleteProfile();
        profile.complete();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
    }

    @Test
    void complete_throwsIncompleteWhenMissingRequirements() {
        var profile = ProfessionalProfile.create(UID);
        assertThatThrownBy(profile::complete)
                .isInstanceOf(IncompleteProfileException.class);
    }

    @Test
    void complete_throwsAlreadyCompletedWhenCalledTwice() {
        var profile = buildCompleteProfile();
        profile.complete();
        assertThatThrownBy(profile::complete)
                .isInstanceOf(ProfileAlreadyCompletedException.class);
    }

    // ── addSkill() ────────────────────────────────────────────────────────

    @Test
    void addSkill_throwsDuplicateForSameNameIgnoringCase() {
        var profile = ProfessionalProfile.create(UID);
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));

        assertThatThrownBy(() -> profile.addSkill(
                new ProfileSkill(UUID.randomUUID(), SkillName.of("JAVA"), SkillLevel.BASIC, DataProvenance.MANUAL)))
                .isInstanceOf(DuplicateSkillException.class);
    }

    @Test
    void addSkill_throwsDuplicateForSameNameIgnoringExtraSpaces() {
        var profile = ProfessionalProfile.create(UID);
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java Script"), SkillLevel.ADVANCED, DataProvenance.MANUAL));

        assertThatThrownBy(() -> profile.addSkill(
                new ProfileSkill(UUID.randomUUID(), SkillName.of("Java  Script"), SkillLevel.BASIC, DataProvenance.MANUAL)))
                .isInstanceOf(DuplicateSkillException.class);
    }

    @Test
    void addSkill_allowsDifferentSkillNames() {
        var profile = ProfessionalProfile.create(UID);
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));

        assertThatCode(() -> profile.addSkill(
                new ProfileSkill(UUID.randomUUID(), SkillName.of("Python"), SkillLevel.BASIC, DataProvenance.MANUAL)))
                .doesNotThrowAnyException();
        assertThat(profile.getProfileSkills()).hasSize(2);
    }

    @Test
    @DisplayName("La vigésima habilidad se acepta cuando el perfil tiene diecinueve")
    void addSkill_shouldAcceptTwentieth_whenProfileHasNineteen() {
        var profile = profileWithSkills(19);

        profile.addSkill(skill("skill-20"));

        assertThat(profile.getProfileSkills()).hasSize(ProfessionalProfile.MAX_SKILLS);
    }

    @Test
    @DisplayName("La habilidad 21 se rechaza con el máximo y el perfil queda igual")
    void addSkill_shouldRejectTwentyFirst_whenProfileHasTwenty() {
        var profile = profileWithSkills(20);

        assertThatThrownBy(() -> profile.addSkill(skill("skill-21")))
                .isInstanceOf(SkillLimitReachedException.class)
                .hasMessage("Ya tienes el máximo de 20 habilidades.");
        assertThat(profile.getProfileSkills()).hasSize(20);
    }

    @Test
    @DisplayName("Con veinte habilidades el máximo se comprueba antes que el duplicado")
    void addSkill_shouldRejectLimitFirst_whenProfileHasTwentyAndSkillIsRepeated() {
        var profile = profileWithSkills(20);

        assertThatThrownBy(() -> profile.addSkill(skill("SKILL-01")))
                .isInstanceOf(SkillLimitReachedException.class);
    }

    @Test
    @DisplayName("Una habilidad que solo difiere en las tildes es la misma")
    void addSkill_shouldRejectDuplicate_whenOnlyAccentsDiffer() {
        var profile = ProfessionalProfile.create(UID);
        profile.addSkill(skill("Programación"));

        assertThatThrownBy(() -> profile.addSkill(skill("programacion")))
                .isInstanceOf(DuplicateSkillException.class);
        assertThat(profile.getProfileSkills()).hasSize(1);
    }

    @Test
    @DisplayName("Un perfil en Borrador puede quedarse sin su última habilidad")
    void removeSkill_shouldRemoveLast_whenProfileIsInProgress() {
        var profile = ProfessionalProfile.create(UID);
        var only = skill("Java");
        profile.addSkill(only);

        profile.removeSkill(only.getId());

        assertThat(profile.getProfileSkills()).isEmpty();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("La última habilidad de un perfil activo no se elimina y el perfil sigue activo")
    void removeSkill_shouldRejectLast_whenProfileIsCompleted() {
        var profile = buildCompleteProfile();
        profile.complete();
        var only = profile.getProfileSkills().get(0);

        assertThatThrownBy(() -> profile.removeSkill(only.getId()))
                .isInstanceOf(SkillNotAllowedException.class)
                .hasMessage("No puedes quedarte sin habilidades con el perfil activo. Agrega otra antes de eliminar esta.");
        assertThat(profile.getProfileSkills()).hasSize(1);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
    }

    @Test
    @DisplayName("Un perfil activo con dos habilidades puede eliminar una")
    void removeSkill_shouldRemove_whenCompletedProfileHasTwo() {
        var profile = buildCompleteProfile();
        profile.addSkill(skill("Python"));
        profile.complete();
        var first = profile.getProfileSkills().get(0);

        profile.removeSkill(first.getId());

        assertThat(profile.getProfileSkills()).hasSize(1);
        assertThat(profile.getProfileSkills().get(0).getSkillName()).isEqualTo("Python");
    }

    @Test
    @DisplayName("Un identificador ajeno responde «no encontrado» antes que la regla de la última habilidad")
    void removeSkill_shouldThrowNotFound_whenIdIsForeignInCompletedProfileWithOne() {
        var profile = buildCompleteProfile();
        profile.complete();

        assertThatThrownBy(() -> profile.removeSkill(UUID.randomUUID()))
                .isInstanceOf(SkillNotFoundException.class);
        assertThat(profile.getProfileSkills()).hasSize(1);
    }

    // ── requestReview() ───────────────────────────────────────────────────

    @Test
    void requestReview_throwsIncompleteWhenProfileIsNotComplete() {
        var profile = ProfessionalProfile.create(UID);

        assertThatThrownBy(profile::requestReview)
                .isInstanceOf(IncompleteProfileException.class);
    }

    @Test
    void requestReview_changesStatusToInReviewWhenComplete() {
        var profile = buildCompleteProfile();

        profile.requestReview();

        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_REVIEW);
    }

    // ── updateTargetRole() ───────────────────────────────────────────────

    @Test
    @DisplayName("Actualizar un rol objetivo que no está en el perfil lanza su código, sin repetir el identificador")
    void updateTargetRole_shouldThrowTargetRoleNotFound_whenRoleIsNotInProfile() {
        var profile = ProfessionalProfile.create(UID);
        var missing = UUID.randomUUID();

        assertThatThrownBy(() -> profile.updateTargetRole(missing, UUID.randomUUID(), "Backend Dev"))
                .isInstanceOf(TargetRoleNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TARGET_ROLE_NOT_FOUND)
                .hasMessage("No encontramos lo que buscabas.");
    }

    @Test
    @DisplayName("Actualizar un rol objetivo del perfil cambia su rol profesional")
    void updateTargetRole_shouldReplaceRole_whenRoleIsInProfile() {
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(role(UUID.randomUUID(), "Backend Dev"));
        var roleId = profile.getTargetRoles().get(0).getId();
        var newProfessionalRole = UUID.randomUUID();

        profile.updateTargetRole(roleId, newProfessionalRole, "Frontend Dev");

        assertThat(profile.getTargetRoles()).singleElement()
                .satisfies(r -> assertThat(r.getProfessionalRoleId()).isEqualTo(newProfessionalRole));
    }

    @Test
    @DisplayName("Sustituir un rol objetivo por otro que ya está en el perfil se rechaza y no cambia nada (CA-2.11.8)")
    void updateTargetRole_shouldThrowDuplicate_whenNewRoleIsAlreadyInProfile() {
        var profile = ProfessionalProfile.create(UID);
        var analyst = UUID.randomUUID();
        var scientist = UUID.randomUUID();
        profile.addTargetRole(role(analyst, "Analista de datos"));
        profile.addTargetRole(role(scientist, "Científico de datos"));
        var analystRoleId = profile.getTargetRoles().get(0).getId();

        assertThatThrownBy(() -> profile.updateTargetRole(analystRoleId, scientist, "Científico de datos"))
                .isInstanceOf(DuplicateTargetRoleException.class)
                .hasMessage("Ese rol objetivo ya está en tu perfil.");
        assertThat(profile.getTargetRoles()).extracting(TargetRole::getProfessionalRoleId)
                .containsExactlyInAnyOrder(analyst, scientist);
    }

    // ── remove*() de un elemento que no es del perfil ────────────────────

    @Test
    @DisplayName("Eliminar un elemento que no es del perfil lanza su «no encontrado» y no cambia nada (CA-2.4.58, 2.4.59, 2.5.23)")
    void remove_shouldThrowNotFound_whenElementIsNotInProfile() {
        var profile = buildCompleteProfile();
        var other = UUID.randomUUID();

        assertThatThrownBy(() -> profile.removeWorkExperience(other)).isInstanceOf(WorkExperienceNotFoundException.class);
        assertThatThrownBy(() -> profile.removeEducation(other)).isInstanceOf(EducationNotFoundException.class)
                .hasMessage("No encontramos lo que buscabas.");
        assertThatThrownBy(() -> profile.removeSkill(other)).isInstanceOf(SkillNotFoundException.class);
        assertThatThrownBy(() -> profile.removeTargetRole(other)).isInstanceOf(TargetRoleNotFoundException.class);
        assertThat(profile.getEducations()).hasSize(1);
        assertThat(profile.getProfileSkills()).hasSize(1);
        assertThat(profile.getTargetRoles()).hasSize(1);
    }

    @Test
    @DisplayName("Eliminar un elemento del perfil lo quita")
    void remove_shouldRemove_whenElementIsInProfile() {
        var profile = buildCompleteProfile();

        profile.removeEducation(profile.getEducations().get(0).getId());
        profile.removeSkill(profile.getProfileSkills().get(0).getId());

        assertThat(profile.getEducations()).isEmpty();
        assertThat(profile.getProfileSkills()).isEmpty();
    }

    @Test
    @DisplayName("Un rol objetivo que no es del perfil responde «no encontrado» antes que la regla del último rol")
    void removeTargetRole_shouldThrowNotFound_whenCompletedProfileHasOneOtherRole() {
        var profile = buildCompleteProfile();
        profile.complete();

        assertThatThrownBy(() -> profile.removeTargetRole(UUID.randomUUID()))
                .isInstanceOf(TargetRoleNotFoundException.class);
    }

    // ── updateSummary() ──────────────────────────────────────────────────

    @Test
    @DisplayName("Borrar el resumen de un perfil en Borrador lo deja vacío (CA-2.3.4)")
    void updateSummary_shouldClear_whenProfileIsInProgress() {
        var profile = ProfessionalProfile.create(UID);
        profile.updateSummary(new ProfessionalSummary("Resumen"));

        profile.updateSummary(null);

        assertThat(profile.getSummary()).isNull();
    }

    @Test
    @DisplayName("Borrar el resumen de un perfil activo se rechaza y lo conserva (CA-2.3.12)")
    void updateSummary_shouldRejectClear_whenProfileIsCompleted() {
        var profile = buildCompleteProfile();
        profile.complete();

        assertThatThrownBy(() -> profile.updateSummary(null))
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new InvalidFieldsException.FieldError("summary", ErrorCode.SUMMARY_NOT_ALLOWED,
                                "No puedes quedarte sin resumen profesional con el perfil activo.")));
        assertThat(profile.getSummary()).isNotNull();
    }

    @Test
    @DisplayName("Los requisitos faltantes usan los códigos del backlog, en orden fijo")
    void getMissingRequirements_shouldUseBacklogCodes_whenProfileIsEmpty() {
        assertThat(ProfessionalProfile.create(UID).getMissingRequirements())
                .containsExactly("NAME", "SUMMARY", "EDUCATION", "SKILLS", "TARGET_ROLES");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    // ── Experiencias laborales: máximo de 4 ──────────────────────────────

    @Test
    @DisplayName("La cuarta experiencia se acepta")
    void addWorkExperience_shouldAcceptFourth_whenProfileHasThree() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < 3; i++) {
            profile.addWorkExperience(experience("Empresa " + i));
        }

        profile.addWorkExperience(experience("Empresa 3"));

        assertThat(profile.getWorkExperiences()).hasSize(4);
    }

    @Test
    @DisplayName("La quinta experiencia se rechaza y la lista sigue con cuatro")
    void addWorkExperience_shouldRejectFifth_whenProfileHasFour() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < 4; i++) {
            profile.addWorkExperience(experience("Empresa " + i));
        }

        assertThatThrownBy(() -> profile.addWorkExperience(experience("Empresa 4")))
                .isInstanceOf(co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceLimitReachedException.class)
                .hasMessage("Ya tienes el máximo de 4 experiencias laborales.");
        assertThat(profile.getWorkExperiences()).hasSize(4);
    }

    @Test
    @DisplayName("Un perfil activo puede quedarse sin su única experiencia y sigue activo")
    void removeWorkExperience_shouldRemoveOnly_whenCompletedProfileHasOne() {
        var profile = buildCompleteProfile();
        var only = experience("ACME");
        profile.addWorkExperience(only);
        profile.complete();

        profile.removeWorkExperience(only.getId());

        assertThat(profile.getWorkExperiences()).isEmpty();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
    }

    // ── Formaciones académicas: máximo de 5 y mínimo de 1 en perfil activo ─

    @Test
    @DisplayName("La quinta formación se acepta")
    void addEducation_shouldAcceptFifth_whenProfileHasFour() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < 4; i++) {
            profile.addEducation(edu("Institución " + i));
        }

        profile.addEducation(edu("Institución 4"));

        assertThat(profile.getEducations()).hasSize(5);
    }

    @Test
    @DisplayName("La sexta formación se rechaza y la lista sigue con cinco")
    void addEducation_shouldRejectSixth_whenProfileHasFive() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < 5; i++) {
            profile.addEducation(edu("Institución " + i));
        }

        assertThatThrownBy(() -> profile.addEducation(edu("Institución 5")))
                .isInstanceOf(co.edu.unicauca.cameia.perfil.domain.exception.EducationLimitReachedException.class)
                .hasMessage("Ya tienes el máximo de 5 formaciones académicas.");
        assertThat(profile.getEducations()).hasSize(5);
    }

    @Test
    @DisplayName("Un perfil en Borrador puede quedarse sin su última formación")
    void removeEducation_shouldRemoveLast_whenProfileIsInProgress() {
        var profile = ProfessionalProfile.create(UID);
        var only = edu("Institución");
        profile.addEducation(only);

        profile.removeEducation(only.getId());

        assertThat(profile.getEducations()).isEmpty();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Un perfil en revisión puede quedarse sin su última formación")
    void removeEducation_shouldRemoveLast_whenProfileIsInReview() {
        var profile = buildCompleteProfile();
        profile.requestReview();
        var only = profile.getEducations().get(0);

        profile.removeEducation(only.getId());

        assertThat(profile.getEducations()).isEmpty();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("La última formación de un perfil activo no se elimina y el perfil sigue activo")
    void removeEducation_shouldRejectLast_whenProfileIsCompleted() {
        var profile = buildCompleteProfile();
        profile.complete();
        var only = profile.getEducations().get(0);

        assertThatThrownBy(() -> profile.removeEducation(only.getId()))
                .isInstanceOf(co.edu.unicauca.cameia.perfil.domain.exception.EducationNotAllowedException.class)
                .hasMessage("No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta.");
        assertThat(profile.getEducations()).hasSize(1);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
    }

    @Test
    @DisplayName("Un perfil activo con dos formaciones puede eliminar una")
    void removeEducation_shouldRemove_whenCompletedProfileHasTwo() {
        var profile = buildCompleteProfile();
        var second = edu("Otra institución");
        profile.addEducation(second);
        profile.complete();

        profile.removeEducation(second.getId());

        assertThat(profile.getEducations()).hasSize(1);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.COMPLETED);
    }

    @Test
    @DisplayName("Un identificador ajeno responde no encontrado y no no-permitido, aunque quede una sola formación")
    void removeEducation_shouldThrowNotFound_whenIdIsForeignInCompletedProfileWithOne() {
        var profile = buildCompleteProfile();
        profile.complete();

        assertThatThrownBy(() -> profile.removeEducation(UUID.randomUUID()))
                .isInstanceOf(co.edu.unicauca.cameia.perfil.domain.exception.EducationNotFoundException.class);
        assertThat(profile.getEducations()).hasSize(1);
    }

    private static WorkExperience experience(String company) {
        return WorkExperience.create(new WorkExperienceDraft(company, "Dev", null, null, null, EmploymentStatus.CURRENT),
                new DateBounds(YearMonth.of(2026, 10), null));
    }

    private static TargetRole role(UUID professionalRoleId, String roleTitle) {
        return new TargetRole(UUID.randomUUID(), professionalRoleId, roleTitle, DataProvenance.MANUAL);
    }

    private static Education edu() {
        return edu("Universidad X");
    }

    private static Education edu(String institution) {
        return Education.create(new EducationDraft(institution, "Ingeniería", EducationLevel.UNDERGRADUATE,
                YearMonth.of(2018, 1), null, false), new DateBounds(YearMonth.of(2026, 10), null));
    }

    @Test
    @DisplayName("Reconstituir un perfil conserva su fecha de creación y su fecha de actualización guardadas")
    void reconstitute_shouldKeepStoredDates_whenProfileIsRebuilt() {
        var created = Instant.parse("2026-01-02T03:04:05Z");
        var updated = Instant.parse("2026-02-03T04:05:06Z");

        var rebuilt = ProfessionalProfile.reconstitute(ProfileId.generate(), new FirebaseUid("uid-rebuild"), null, null, null,
                null, DataProvenance.MANUAL, ProfileStatus.IN_PROGRESS, ReviewStatus.PENDING_REVIEW, created, updated,
                List.of(), List.of(), List.of(), List.of());

        assertThat(rebuilt.getCreatedAt()).isEqualTo(created);
        assertThat(rebuilt.getUpdatedAt()).isEqualTo(updated);
    }

    private static ProfileSkill skill(String name) {
        return new ProfileSkill(UUID.randomUUID(), SkillName.of(name), SkillLevel.BASIC, DataProvenance.MANUAL);
    }

    private static ProfessionalProfile profileWithSkills(int count) {
        var profile = ProfessionalProfile.create(UID);
        for (int index = 1; index <= count; index++) {
            profile.addSkill(skill(String.format("skill-%02d", index)));
        }
        return profile;
    }

    private static ProfessionalProfile buildCompleteProfile() {
        var profile = ProfessionalProfile.create(UID);
        profile.updateName(new ProfileName("Ana Sofía"));
        profile.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Spring Boot."));
        profile.addTargetRole(role(UUID.randomUUID(), "Backend Dev"));
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), SkillName.of("Java"), SkillLevel.ADVANCED, DataProvenance.MANUAL));
        profile.addEducation(edu());
        return profile;
    }
}
