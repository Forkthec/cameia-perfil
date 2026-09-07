package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
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
import co.edu.unicauca.cameia.perfil.domain.model.Seniority;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileAppService {

    private static final Logger log = LoggerFactory.getLogger(ProfileAppService.class);
    private final ProfessionalProfileRepository repository;

    ProfileAppService(ProfessionalProfileRepository repository) { this.repository = repository; }

    // ── CM-16 ────────────────────────────────────────────────────────────

    @Transactional
    public ProfessionalProfile createProfile(CreateProfileCommand command) {
        var uid = new FirebaseUid(command.firebaseUid());
        if (repository.existsByFirebaseUid(uid)) throw new ProfileAlreadyExistsException();
        var profile = ProfessionalProfile.create(uid);
        repository.save(profile);
        log.info("perfil creado id={}", profile.getId().value());
        return profile;
    }

    // ── CM-17 ────────────────────────────────────────────────────────────

    @Transactional
    public ProfessionalProfile updateProfileInfo(UpdateProfileInfoCommand cmd) {
        var p = load(cmd.profileId());
        if (cmd.name() != null) p.updateName(new ProfileName(cmd.name()));
        if (cmd.headline() != null) p.updateHeadline(cmd.headline());
        if (cmd.summary() != null) p.updateSummary(new ProfessionalSummary(cmd.summary()));
        if (cmd.preferredModality() != null) p.updatePreferredModality(WorkModality.valueOf(cmd.preferredModality()));
        if (cmd.provenance() != null) p.updateProvenance(DataProvenance.valueOf(cmd.provenance()));
        repository.save(p); return p;
    }

    // ── CM-18 ────────────────────────────────────────────────────────────

    @Transactional
    public ProfessionalProfile addWorkExperience(AddWorkExperienceCommand cmd) {
        var p = load(cmd.profileId());
        p.addWorkExperience(new WorkExperience(UUID.randomUUID(), cmd.company(), cmd.position(), cmd.description(),
                YearMonth.parse(cmd.startDate()), cmd.endDate() != null ? YearMonth.parse(cmd.endDate()) : null,
                EmploymentStatus.valueOf(cmd.employmentStatus()), Seniority.valueOf(cmd.seniority()), DataProvenance.valueOf(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeWorkExperience(UUID profileId, UUID expId) {
        var p = load(profileId); p.removeWorkExperience(expId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addEducation(AddEducationCommand cmd) {
        var p = load(cmd.profileId());
        p.addEducation(new Education(UUID.randomUUID(), cmd.institution(), cmd.degree(), cmd.fieldOfStudy(),
                EducationLevel.valueOf(cmd.level()), YearMonth.parse(cmd.startDate()),
                cmd.endDate() != null ? YearMonth.parse(cmd.endDate()) : null, cmd.inProgress(), DataProvenance.valueOf(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeEducation(UUID profileId, UUID eduId) {
        var p = load(profileId); p.removeEducation(eduId); repository.save(p); return p;
    }

    // ── CM-19 ────────────────────────────────────────────────────────────

    @Transactional
    public ProfessionalProfile updateSalaryExpectation(UpdateSalaryExpectationCommand cmd) {
        var p = load(cmd.profileId());
        p.updateSalaryExpectation(new SalaryExpectation(cmd.amount()));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addSkill(AddSkillCommand cmd) {
        var p = load(cmd.profileId());
        p.addSkill(new ProfileSkill(UUID.randomUUID(), cmd.skillName(), SkillLevel.valueOf(cmd.level()), DataProvenance.valueOf(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeSkill(UUID profileId, UUID skillId) {
        var p = load(profileId); p.removeSkill(skillId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile requestReview(UUID profileId) {
        var p = load(profileId); p.requestReview(); repository.save(p); return p;
    }

    // ── Shared ────────────────────────────────────────────────────────────

    public ProfessionalProfile loadProfile(UUID profileId) { return load(profileId); }

    private ProfessionalProfile load(UUID profileId) {
        return repository.findById(ProfileId.of(profileId))
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
    }
}
