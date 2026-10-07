package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CommandValues;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.SalaryExpectation;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.EDUCATION_LEVEL_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.EMPLOYMENT_STATUS_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.END_DATE_INVALID_FORMAT;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.PREFERRED_MODALITY_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.PROVENANCE_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.SKILL_LEVEL_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.START_DATE_INVALID_FORMAT;

@Service
@Transactional(readOnly = true)
public class ProfileAppService {

    private final ProfessionalProfileRepository repository;
    private final ProfessionalRoleRepository roleRepository;

    ProfileAppService(ProfessionalProfileRepository repository, ProfessionalRoleRepository roleRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public ProfessionalProfile updateProfileInfo(UpdateProfileInfoCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        if (cmd.name() != null) p.updateName(new ProfileName(cmd.name()));
        if (cmd.summary() != null) p.updateSummary(new ProfessionalSummary(cmd.summary()));
        if (cmd.preferredModality() != null) p.updatePreferredModality(
                CommandValues.option(WorkModality.class, cmd.preferredModality(), PREFERRED_MODALITY_INVALID_VALUE,
                        "preferredModality"));
        if (cmd.provenance() != null) p.updateProvenance(provenance(cmd.provenance()));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addWorkExperience(AddWorkExperienceCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        p.addWorkExperience(new WorkExperience(UUID.randomUUID(), cmd.company(), cmd.position(), cmd.description(),
                startDate(cmd.startDate(), false), endDate(cmd.endDate(), false),
                CommandValues.option(EmploymentStatus.class, cmd.employmentStatus(), EMPLOYMENT_STATUS_INVALID_VALUE,
                        "employmentStatus"),
                provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeWorkExperience(UUID profileId, String uid, UUID expId) {
        var p = loadForUser(profileId, uid); p.removeWorkExperience(expId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addEducation(AddEducationCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        p.addEducation(new Education(UUID.randomUUID(), cmd.institution(), cmd.degree(), cmd.fieldOfStudy(),
                CommandValues.option(EducationLevel.class, cmd.level(), EDUCATION_LEVEL_INVALID_VALUE, "level"),
                startDate(cmd.startDate(), true), endDate(cmd.endDate(), true),
                cmd.inProgress(), provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeEducation(UUID profileId, String uid, UUID eduId) {
        var p = loadForUser(profileId, uid); p.removeEducation(eduId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile updateSalaryExpectation(UpdateSalaryExpectationCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        p.updateSalaryExpectation(new SalaryExpectation(cmd.amount()));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addSkill(AddSkillCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        p.addSkill(new ProfileSkill(UUID.randomUUID(), cmd.skillName(),
                CommandValues.option(SkillLevel.class, cmd.level(), SKILL_LEVEL_INVALID_VALUE, "level"),
                provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeSkill(UUID profileId, String uid, UUID skillId) {
        var p = loadForUser(profileId, uid); p.removeSkill(skillId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile requestReview(UUID profileId, String uid) {
        var p = loadForUser(profileId, uid); p.requestReview(); repository.save(p); return p;
    }

    public ProfessionalProfile getProfile(UUID id, String uid) { return loadForUser(id, uid); }

    @Transactional
    public ProfessionalProfile addTargetRole(AddTargetRoleCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        var role = roleRepository.findById(cmd.professionalRoleId())
                .orElseThrow(() -> new ProfessionalRoleNotFoundException(cmd.professionalRoleId()));
        p.addTargetRole(new TargetRole(UUID.randomUUID(), role.id(), role.nombre(), provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile updateTargetRole(UpdateTargetRoleCommand cmd) {
        var p = loadForUser(cmd.profileId(), cmd.uid());
        var role = roleRepository.findById(cmd.professionalRoleId())
                .orElseThrow(() -> new ProfessionalRoleNotFoundException(cmd.professionalRoleId()));
        p.updateTargetRole(cmd.roleId(), role.id(), role.nombre());
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeTargetRole(UUID profileId, String uid, UUID roleId) {
        var p = loadForUser(profileId, uid); p.removeTargetRole(roleId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile completeProfile(UUID profileId, String uid) {
        var p = loadForUser(profileId, uid);
        p.complete();
        repository.save(p);
        return p;
    }

    // ── Shared ────────────────────────────────────────────────────────────

    private static DataProvenance provenance(String value) {
        return CommandValues.option(DataProvenance.class, value, PROVENANCE_INVALID_VALUE, "provenance");
    }

    private static YearMonth startDate(String value, boolean yearOnly) {
        return CommandValues.yearMonth(value, yearOnly, START_DATE_INVALID_FORMAT, "startDate");
    }

    private static YearMonth endDate(String value, boolean yearOnly) {
        return CommandValues.yearMonth(value, yearOnly, END_DATE_INVALID_FORMAT, "endDate");
    }

    public ProfessionalProfile loadProfile(UUID profileId) { return load(profileId); }

    private ProfessionalProfile load(UUID profileId) {
        return repository.findById(ProfileId.of(profileId))
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
    }

    private ProfessionalProfile loadForUser(UUID profileId, String uid) {
        var owner = FirebaseUid.required(uid);
        var p = load(profileId);
        if (!p.getFirebaseUid().equals(owner)) throw new ProfileAccessDeniedException();
        return p;
    }
}
