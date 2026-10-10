package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
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
import co.edu.unicauca.cameia.perfil.domain.model.FieldValues;
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

import java.time.Clock;
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
    /** Reloj en UTC; las reglas de fechas de la experiencia y la formación lo usan para saber qué mes es hoy. */
    private final Clock clock;

    ProfileAppService(ProfessionalProfileRepository repository, ProfessionalRoleRepository roleRepository, Clock clock) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.clock = clock;
    }

    @Transactional
    public ProfessionalProfile updateProfileInfo(UpdateProfileInfoCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        if (cmd.name() != null) p.updateName(new ProfileName(cmd.name()));
        // Un resumen en blanco lo borra (CA-2.3.4).
        if (cmd.summary() != null) p.updateSummary(cmd.summary().isBlank() ? null : new ProfessionalSummary(cmd.summary()));
        if (cmd.preferredModality() != null) p.updatePreferredModality(
                FieldValues.option(WorkModality.class, cmd.preferredModality(), PREFERRED_MODALITY_INVALID_VALUE,
                        "preferredModality"));
        if (cmd.provenance() != null) p.updateProvenance(provenance(cmd.provenance()));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addWorkExperience(AddWorkExperienceCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        p.addWorkExperience(new WorkExperience(UUID.randomUUID(), cmd.company(), cmd.position(), cmd.description(),
                startDate(cmd.startDate(), false), endDate(cmd.endDate(), false),
                FieldValues.option(EmploymentStatus.class, cmd.employmentStatus(), EMPLOYMENT_STATUS_INVALID_VALUE,
                        "employmentStatus"),
                provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeWorkExperience(UUID profileId, String uid, UUID expId) {
        var p = loadForUserForUpdate(profileId, uid); p.removeWorkExperience(expId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addEducation(AddEducationCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        p.addEducation(new Education(UUID.randomUUID(), cmd.institution(), cmd.degree(), cmd.fieldOfStudy(),
                FieldValues.option(EducationLevel.class, cmd.level(), EDUCATION_LEVEL_INVALID_VALUE, "level"),
                startDate(cmd.startDate(), true), endDate(cmd.endDate(), true),
                cmd.inProgress(), provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeEducation(UUID profileId, String uid, UUID eduId) {
        var p = loadForUserForUpdate(profileId, uid); p.removeEducation(eduId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile updateSalaryExpectation(UpdateSalaryExpectationCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        p.updateSalaryExpectation(new SalaryExpectation(cmd.amount()));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addSkill(AddSkillCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        p.addSkill(new ProfileSkill(UUID.randomUUID(), cmd.skillName(),
                FieldValues.option(SkillLevel.class, cmd.level(), SKILL_LEVEL_INVALID_VALUE, "level"),
                provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeSkill(UUID profileId, String uid, UUID skillId) {
        var p = loadForUserForUpdate(profileId, uid); p.removeSkill(skillId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile requestReview(UUID profileId, String uid) {
        var p = loadForUserForUpdate(profileId, uid); p.requestReview(); repository.save(p); return p;
    }

    public ProfessionalProfile getProfile(UUID id, String uid) { return loadForUser(id, uid); }

    @Transactional
    public ProfessionalProfile addTargetRole(AddTargetRoleCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        var role = roleRepository.findById(cmd.professionalRoleId())
                .orElseThrow(ProfessionalRoleNotFoundException::new);
        p.addTargetRole(new TargetRole(UUID.randomUUID(), role.id(), role.nombre(), provenance(cmd.provenance())));
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile updateTargetRole(UpdateTargetRoleCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        var role = roleRepository.findById(cmd.professionalRoleId())
                .orElseThrow(ProfessionalRoleNotFoundException::new);
        p.updateTargetRole(cmd.roleId(), role.id(), role.nombre());
        repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile removeTargetRole(UUID profileId, String uid, UUID roleId) {
        var p = loadForUserForUpdate(profileId, uid); p.removeTargetRole(roleId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile completeProfile(UUID profileId, String uid) {
        var p = loadForUserForUpdate(profileId, uid);
        p.complete();
        repository.save(p);
        return p;
    }

    // ── Shared ────────────────────────────────────────────────────────────

    private static DataProvenance provenance(String value) {
        return FieldValues.option(DataProvenance.class, value, PROVENANCE_INVALID_VALUE, "provenance");
    }

    private static YearMonth startDate(String value, boolean yearOnly) {
        return FieldValues.yearMonth(value, yearOnly, START_DATE_INVALID_FORMAT, "startDate");
    }

    private static YearMonth endDate(String value, boolean yearOnly) {
        return FieldValues.yearMonth(value, yearOnly, END_DATE_INVALID_FORMAT, "endDate");
    }

    public ProfessionalProfile loadProfile(UUID profileId) { return load(profileId); }

    private ProfessionalProfile load(UUID profileId) {
        return repository.findById(ProfileId.of(profileId))
                .orElseThrow(ProfileNotFoundException::new);
    }

    private ProfessionalProfile loadForUser(UUID profileId, String uid) {
        var owner = FirebaseUid.required(uid);
        return checkOwner(load(profileId), owner);
    }

    /** Carga el perfil de quien llama bloqueando su fila; todo caso de uso que modifica un perfil empieza aquí. */
    private ProfessionalProfile loadForUserForUpdate(UUID profileId, String uid) {
        var owner = FirebaseUid.required(uid);
        var p = repository.findByIdForUpdate(ProfileId.of(profileId)).orElseThrow(ProfileNotFoundException::new);
        return checkOwner(p, owner);
    }

    private static ProfessionalProfile checkOwner(ProfessionalProfile p, FirebaseUid owner) {
        if (!p.getFirebaseUid().equals(owner)) throw new ProfileAccessDeniedException();
        return p;
    }
}
