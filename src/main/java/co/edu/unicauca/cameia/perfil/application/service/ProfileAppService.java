package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.Seniority;
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

    ProfileAppService(ProfessionalProfileRepository repository) {
        this.repository = repository;
    }

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
        var profile = load(cmd.profileId());
        if (cmd.name() != null) profile.updateName(new ProfileName(cmd.name()));
        if (cmd.headline() != null) profile.updateHeadline(cmd.headline());
        if (cmd.summary() != null) profile.updateSummary(new ProfessionalSummary(cmd.summary()));
        if (cmd.preferredModality() != null) profile.updatePreferredModality(WorkModality.valueOf(cmd.preferredModality()));
        if (cmd.provenance() != null) profile.updateProvenance(DataProvenance.valueOf(cmd.provenance()));
        repository.save(profile);
        return profile;
    }

    // ── CM-18 ────────────────────────────────────────────────────────────

    @Transactional
    public ProfessionalProfile addWorkExperience(AddWorkExperienceCommand cmd) {
        var profile = load(cmd.profileId());
        var exp = new WorkExperience(UUID.randomUUID(), cmd.company(), cmd.position(), cmd.description(),
                YearMonth.parse(cmd.startDate()),
                cmd.endDate() != null ? YearMonth.parse(cmd.endDate()) : null,
                EmploymentStatus.valueOf(cmd.employmentStatus()),
                Seniority.valueOf(cmd.seniority()),
                DataProvenance.valueOf(cmd.provenance()));
        profile.addWorkExperience(exp);
        repository.save(profile);
        return profile;
    }

    @Transactional
    public ProfessionalProfile removeWorkExperience(UUID profileId, UUID expId) {
        var profile = load(profileId);
        profile.removeWorkExperience(expId);
        repository.save(profile);
        return profile;
    }

    @Transactional
    public ProfessionalProfile addEducation(AddEducationCommand cmd) {
        var profile = load(cmd.profileId());
        var edu = new Education(UUID.randomUUID(), cmd.institution(), cmd.degree(), cmd.fieldOfStudy(),
                EducationLevel.valueOf(cmd.level()),
                YearMonth.parse(cmd.startDate()),
                cmd.endDate() != null ? YearMonth.parse(cmd.endDate()) : null,
                cmd.inProgress(), DataProvenance.valueOf(cmd.provenance()));
        profile.addEducation(edu);
        repository.save(profile);
        return profile;
    }

    @Transactional
    public ProfessionalProfile removeEducation(UUID profileId, UUID eduId) {
        var profile = load(profileId);
        profile.removeEducation(eduId);
        repository.save(profile);
        return profile;
    }

    // ── Shared ────────────────────────────────────────────────────────────

    public ProfessionalProfile loadProfile(UUID profileId) { return load(profileId); }

    private ProfessionalProfile load(UUID profileId) {
        return repository.findById(ProfileId.of(profileId))
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
    }
}
