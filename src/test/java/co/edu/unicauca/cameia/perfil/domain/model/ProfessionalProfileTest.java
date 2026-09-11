package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyCompletedException;
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
