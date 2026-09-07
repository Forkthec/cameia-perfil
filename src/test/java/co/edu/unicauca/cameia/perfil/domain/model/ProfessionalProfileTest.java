package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.IncompleteProfileException;
import co.edu.unicauca.cameia.perfil.domain.exception.LastTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.exception.MaxTargetRolesExceededException;
import org.junit.jupiter.api.Test;

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

    // ── isComplete() ──────────────────────────────────────────────────────

    @Test
    void isComplete_requiresNameSummaryAndAtLeastOneRole() {
        var profile = ProfessionalProfile.create(UID);

        assertThat(profile.isComplete()).isFalse();

        profile.updateName(new ProfileName("Ana Sofía"));
        assertThat(profile.isComplete()).isFalse();

        profile.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Spring Boot."));
        assertThat(profile.isComplete()).isFalse();

        profile.addTargetRole(role("Backend Dev", Seniority.SEMI_SENIOR));
        assertThat(profile.isComplete()).isTrue();
    }

    // ── addTargetRole() ───────────────────────────────────────────────────

    @Test
    void addTargetRole_throwsMaxExceededWhenLimitReached() {
        var profile = ProfessionalProfile.create(UID);
        for (int i = 0; i < ProfessionalProfile.MAX_TARGET_ROLES; i++) {
            profile.addTargetRole(role("Role " + i, Seniority.JUNIOR));
        }

        assertThatThrownBy(() -> profile.addTargetRole(role("Extra", Seniority.SENIOR)))
                .isInstanceOf(MaxTargetRolesExceededException.class);
    }

    @Test
    void addTargetRole_throwsDuplicateForSameTitleAndSeniority() {
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(role("Backend Dev", Seniority.SENIOR));

        // mismo título en mayúsculas → duplicado
        assertThatThrownBy(() -> profile.addTargetRole(role("BACKEND DEV", Seniority.SENIOR)))
                .isInstanceOf(DuplicateTargetRoleException.class);
    }

    @Test
    void addTargetRole_allowsSameTitleWithDifferentSeniority() {
        var profile = ProfessionalProfile.create(UID);
        profile.addTargetRole(role("Backend Dev", Seniority.JUNIOR));

        assertThatCode(() -> profile.addTargetRole(role("Backend Dev", Seniority.SENIOR)))
                .doesNotThrowAnyException();
        assertThat(profile.getTargetRoles()).hasSize(2);
    }

    // ── removeTargetRole() ────────────────────────────────────────────────

    @Test
    void removeTargetRole_throwsLastTargetRoleWhenOnlyOneRemains() {
        var profile = ProfessionalProfile.create(UID);
        var single = role("Only Role", Seniority.JUNIOR);
        profile.addTargetRole(single);

        assertThatThrownBy(() -> profile.removeTargetRole(single.getId()))
                .isInstanceOf(LastTargetRoleException.class);
    }

    @Test
    void removeTargetRole_removesCorrectlyWhenMultipleExist() {
        var profile = ProfessionalProfile.create(UID);
        var first = role("Role A", Seniority.JUNIOR);
        var second = role("Role B", Seniority.SENIOR);
        profile.addTargetRole(first);
        profile.addTargetRole(second);

        profile.removeTargetRole(first.getId());

        assertThat(profile.getTargetRoles()).hasSize(1);
        assertThat(profile.getTargetRoles().get(0).getId()).isEqualTo(second.getId());
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

    private static TargetRole role(String title, Seniority seniority) {
        return new TargetRole(UUID.randomUUID(), title, seniority, DataProvenance.MANUAL);
    }

    private static ProfessionalProfile buildCompleteProfile() {
        var profile = ProfessionalProfile.create(UID);
        profile.updateName(new ProfileName("Ana Sofía"));
        profile.updateSummary(new ProfessionalSummary("Desarrolladora backend con experiencia en Spring Boot."));
        profile.addTargetRole(role("Backend Dev", Seniority.SEMI_SENIOR));
        return profile;
    }
}
