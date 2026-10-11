package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.BirthDateUnavailableException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAccessDeniedException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.DateBounds;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationDraft;
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
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperienceDraft;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.BirthDateReplica;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import java.util.function.Function;

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
    /** Réplica local de la fecha de nacimiento: es la única fuente de la fecha; Perfil no llama a Cuentas. */
    private final BirthDateReplica birthDateReplica;

    ProfileAppService(ProfessionalProfileRepository repository, ProfessionalRoleRepository roleRepository, Clock clock,
            BirthDateReplica birthDateReplica) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.clock = clock;
        this.birthDateReplica = birthDateReplica;
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
        var profile = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        var draft = new WorkExperienceDraft(cmd.company(), cmd.position(), cmd.description(),
                startDate(cmd.startDate()), endDate(cmd.endDate()),
                employmentStatus(cmd.employmentStatus()));
        boolean hasDates = draft.startDate() != null || draft.endDate() != null;
        profile.addWorkExperience(createWithBirthMonth(cmd.uid(), hasDates, bounds -> WorkExperience.create(draft, bounds)));
        repository.save(profile);
        return profile;
    }

    /**
     * Crea un registro con fechas aplicando también las reglas del mes de nacimiento.
     *
     * <p>Un registro sin ninguna fecha no lee la réplica. Con fechas, las reglas del registro corren primero con el
     * mes de nacimiento que haya (así todos los errores de campo salen a la vez, incluidos los de edad); solo si el
     * registro es válido y la réplica no tiene la fecha del Usuario se responde 503, porque entonces no se pueden
     * comprobar las reglas de nacimiento y guardarlo sería aceptar algo sin verificar. La fecha nunca se registra.</p>
     *
     * @param uid      identidad del dueño del perfil
     * @param hasDates si el registro trae fecha de inicio o de fin
     * @param factory  fábrica del dominio que aplica las reglas con los límites recibidos
     * @param <T>      experiencia o formación
     * @return el registro válido
     * @throws BirthDateUnavailableException si el registro es válido, trae fechas y la réplica no tiene la fecha
     */
    private <T> T createWithBirthMonth(String uid, boolean hasDates, Function<DateBounds, T> factory) {
        var currentMonth = YearMonth.now(clock);
        if (!hasDates) {
            return factory.apply(new DateBounds(currentMonth, null));
        }
        var birthMonth = birthDateReplica.findBirthDate(FirebaseUid.required(uid)).map(YearMonth::from);
        var created = factory.apply(new DateBounds(currentMonth, birthMonth.orElse(null)));
        if (birthMonth.isEmpty()) {
            throw new BirthDateUnavailableException();
        }
        return created;
    }

    /** Estado ausente → {@code null}, que el dominio informa como requerido; un valor desconocido se rechaza aquí con su código. */
    private static EmploymentStatus employmentStatus(String value) {
        return value == null ? null
                : FieldValues.option(EmploymentStatus.class, value, EMPLOYMENT_STATUS_INVALID_VALUE, "employmentStatus");
    }

    @Transactional
    public ProfessionalProfile removeWorkExperience(UUID profileId, String uid, UUID expId) {
        var p = loadForUserForUpdate(profileId, uid); p.removeWorkExperience(expId); repository.save(p); return p;
    }

    @Transactional
    public ProfessionalProfile addEducation(AddEducationCommand cmd) {
        var profile = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        var draft = new EducationDraft(cmd.institution(), cmd.degree(), educationLevel(cmd.level()),
                startDate(cmd.startDate()), endDate(cmd.endDate()), cmd.inProgress());
        boolean hasDates = draft.startDate() != null || draft.endDate() != null;
        profile.addEducation(createWithBirthMonth(cmd.uid(), hasDates, bounds -> Education.create(draft, bounds)));
        repository.save(profile);
        return profile;
    }

    /** Nivel ausente → {@code null}, que el dominio informa como requerido; un valor desconocido se rechaza aquí con su código. */
    private static EducationLevel educationLevel(String value) {
        return value == null ? null
                : FieldValues.option(EducationLevel.class, value, EDUCATION_LEVEL_INVALID_VALUE, "level");
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

    /**
     * Agrega un rol objetivo al perfil de quien llama. La procedencia siempre es manual: el servidor la fija.
     *
     * @param cmd perfil, usuario que llama y rol del catálogo a agregar
     * @return el perfil con el nuevo rol objetivo
     * @throws ProfessionalRoleNotFoundException si el rol no existe en el catálogo
     * @throws InvalidFieldsException            si el rol del catálogo está inactivo
     * @throws MaxTargetRolesExceededException   si el perfil ya tiene el máximo de roles objetivo
     * @throws DuplicateTargetRoleException      si el rol ya está en el perfil
     */
    @Transactional
    public ProfessionalProfile addTargetRole(AddTargetRoleCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        var role = selectableRole(cmd.professionalRoleId());
        // Un rol agregado a mano nunca trae origen de IA: la procedencia la fija el servidor.
        p.addTargetRole(new TargetRole(UUID.randomUUID(), role.id(), role.nombre(), DataProvenance.MANUAL));
        repository.save(p); return p;
    }

    /**
     * Sustituye el rol del catálogo de un rol objetivo; conserva su identificador, su procedencia y su lugar en la lista.
     *
     * @param cmd perfil, usuario que llama, rol objetivo a sustituir y nuevo rol del catálogo
     * @return el perfil con el rol objetivo sustituido
     * @throws ProfessionalRoleNotFoundException si el nuevo rol no existe en el catálogo
     * @throws InvalidFieldsException            si el nuevo rol del catálogo está inactivo
     * @throws TargetRoleNotFoundException       si el rol objetivo no está en el perfil
     * @throws DuplicateTargetRoleException      si el nuevo rol ya está en el perfil
     */
    @Transactional
    public ProfessionalProfile updateTargetRole(UpdateTargetRoleCommand cmd) {
        var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
        var role = selectableRole(cmd.professionalRoleId());
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

    private static YearMonth startDate(String value) {
        return FieldValues.yearMonth(value, START_DATE_INVALID_FORMAT, "startDate");
    }

    private static YearMonth endDate(String value) {
        return FieldValues.yearMonth(value, END_DATE_INVALID_FORMAT, "endDate");
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

    /** Busca el rol en el catálogo y exige que se pueda elegir: 404 si no existe, 422 si está inactivo. */
    private ProfessionalRole selectableRole(UUID professionalRoleId) {
        return roleRepository.findById(professionalRoleId)
                .orElseThrow(ProfessionalRoleNotFoundException::new)
                .requireSelectable();
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
