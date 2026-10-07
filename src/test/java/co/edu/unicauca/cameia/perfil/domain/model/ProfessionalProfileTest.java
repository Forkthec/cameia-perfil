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
import co.edu.unicauca.cameia.perfil.domain.exception.SkillNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.TargetRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.WorkExperienceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
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

        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));
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
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));

        assertThatThrownBy(() -> profile.addSkill(
                new ProfileSkill(UUID.randomUUID(), "JAVA", SkillLevel.BASIC, DataProvenance.MANUAL)))
                .isInstanceOf(DuplicateSkillException.class);
    }

    @Test
    void addSkill_throwsDuplicateForSameNameIgnoringExtraSpaces() {
        var profile = ProfessionalProfile.create(UID);
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java Script", SkillLevel.ADVANCED, DataProvenance.MANUAL));

        assertThatThrownBy(() -> profile.addSkill(
                new ProfileSkill(UUID.randomUUID(), "Java  Script", SkillLevel.BASIC, DataProvenance.MANUAL)))
                .isInstanceOf(DuplicateSkillException.class);
    }

    @Test
    void addSkill_allowsDifferentSkillNames() {
        var profile = ProfessionalProfile.create(UID);
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));

        assertThatCode(() -> profile.addSkill(
                new ProfileSkill(UUID.randomUUID(), "Python", SkillLevel.BASIC, DataProvenance.MANUAL)))
                .doesNotThrowAnyException();
        assertThat(profile.getProfileSkills()).hasSize(2);
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

    private static TargetRole role(UUID professionalRoleId, String roleTitle) {
        return new TargetRole(UUID.randomUUID(), professionalRoleId, roleTitle, DataProvenance.MANUAL);
    }

    private static Education edu() {
        return new Education(UUID.randomUUID(), "Universidad X", "Ingeniería", "Sistemas",
                EducationLevel.UNDERGRADUATE, YearMonth.of(2018, 1), null, false, DataProvenance.MANUAL);
    }

    private static ProfessionalProfile buildCompleteProfile() {
        var profile = ProfessionalProfile.create(UID);
        profile.updateName(new ProfileName("Ana Sofía"));
        profile.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Spring Boot."));
        profile.addTargetRole(role(UUID.randomUUID(), "Backend Dev"));
        profile.addSkill(new ProfileSkill(UUID.randomUUID(), "Java", SkillLevel.ADVANCED, DataProvenance.MANUAL));
        profile.addEducation(edu());
        return profile;
    }
}
