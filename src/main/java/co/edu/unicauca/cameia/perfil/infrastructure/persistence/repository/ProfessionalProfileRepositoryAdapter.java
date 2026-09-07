package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSkill;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.ReviewStatus;
import co.edu.unicauca.cameia.perfil.domain.model.SalaryExpectation;
import co.edu.unicauca.cameia.perfil.domain.model.Seniority;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.EducationEntity;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfileSkillEntity;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalProfileEntity;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.TargetRoleEntity;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.WorkExperienceEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Pieza 3 del patrón de tres piezas (AGENTS §5.3): adaptador que implementa el puerto de dominio
 * usando Spring Data JPA. El mapping domain ↔ entity se hace con métodos privados aquí (§5.5.2 opción a).
 */
@Repository
class ProfessionalProfileRepositoryAdapter implements ProfessionalProfileRepository {

    private final ProfessionalProfileJpaRepository jpa;

    ProfessionalProfileRepositoryAdapter(ProfessionalProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(ProfessionalProfile profile) {
        jpa.save(toEntity(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalProfile> findById(ProfileId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByFirebaseUid(FirebaseUid firebaseUid) {
        return jpa.existsByFirebaseUid(firebaseUid.value());
    }

    // ── Domain → Entity ────────────────────────────────────────────────
    private ProfessionalProfileEntity toEntity(ProfessionalProfile d) {
        ProfessionalProfileEntity e = new ProfessionalProfileEntity();
        e.setId(d.getId().value());
        e.setFirebaseUid(d.getFirebaseUid().value());
        e.setName(d.getName() != null ? d.getName().value() : null);
        e.setSummary(d.getSummary() != null ? d.getSummary().value() : null);
        e.setSalaryExpectation(d.getSalaryExpectation() != null ? d.getSalaryExpectation().amount() : null);
        e.setPreferredModality(d.getPreferredModality());
        e.setProvenance(d.getProvenance());
        e.setReviewStatus(d.getReviewStatus());
        e.setStatus(d.getStatus());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());

        List<WorkExperienceEntity> exps = d.getWorkExperiences().stream()
                .map(exp -> toWorkExpEntity(exp, e)).toList();
        e.setWorkExperiences(exps);

        List<EducationEntity> edus = d.getEducations().stream()
                .map(edu -> toEducationEntity(edu, e)).toList();
        e.setEducations(edus);

        List<TargetRoleEntity> roles = d.getTargetRoles().stream()
                .map(role -> toTargetRoleEntity(role, e)).toList();
        e.setTargetRoles(roles);

        List<ProfileSkillEntity> skills = d.getProfileSkills().stream()
                .map(skill -> toSkillEntity(skill, e)).toList();
        e.setProfileSkills(skills);

        return e;
    }

    private WorkExperienceEntity toWorkExpEntity(WorkExperience d, ProfessionalProfileEntity parent) {
        WorkExperienceEntity e = new WorkExperienceEntity();
        e.setId(d.getId());
        e.setProfile(parent);
        e.setCompany(d.getCompany());
        e.setPosition(d.getPosition());
        e.setDescription(d.getDescription());
        e.setStartDate(toFirstDayOfMonth(d.getStartDate()));
        e.setEndDate(d.getEndDate() != null ? toFirstDayOfMonth(d.getEndDate()) : null);
        e.setEmploymentStatus(d.getEmploymentStatus());
        e.setSeniority(d.getSeniority());
        e.setProvenance(d.getProvenance());
        return e;
    }

    private EducationEntity toEducationEntity(Education d, ProfessionalProfileEntity parent) {
        EducationEntity e = new EducationEntity();
        e.setId(d.getId());
        e.setProfile(parent);
        e.setInstitution(d.getInstitution());
        e.setDegree(d.getDegree());
        e.setFieldOfStudy(d.getFieldOfStudy());
        e.setLevel(d.getLevel());
        e.setStartDate(toFirstDayOfMonth(d.getStartDate()));
        e.setEndDate(d.getEndDate() != null ? toFirstDayOfMonth(d.getEndDate()) : null);
        e.setInProgress(d.isInProgress());
        e.setProvenance(d.getProvenance());
        return e;
    }

    private TargetRoleEntity toTargetRoleEntity(TargetRole d, ProfessionalProfileEntity parent) {
        TargetRoleEntity e = new TargetRoleEntity();
        e.setId(d.getId());
        e.setProfile(parent);
        e.setRoleTitle(d.getTitle());
        e.setSeniority(d.getSeniority());
        e.setProvenance(d.getProvenance());
        e.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        e.setCreatedAt(Instant.now());
        return e;
    }

    private ProfileSkillEntity toSkillEntity(ProfileSkill d, ProfessionalProfileEntity parent) {
        ProfileSkillEntity e = new ProfileSkillEntity();
        e.setId(d.getId());
        e.setProfile(parent);
        e.setSkillName(d.getSkillName());
        e.setLevel(d.getLevel());
        e.setProvenance(d.getProvenance());
        return e;
    }

    // ── Entity → Domain ────────────────────────────────────────────────
    private ProfessionalProfile toDomain(ProfessionalProfileEntity e) {
        List<WorkExperience> exps = e.getWorkExperiences().stream()
                .map(this::toWorkExpDomain).toList();
        List<Education> edus = e.getEducations().stream()
                .map(this::toEducationDomain).toList();
        List<TargetRole> roles = e.getTargetRoles().stream()
                .map(this::toTargetRoleDomain).toList();
        List<ProfileSkill> skills = e.getProfileSkills().stream()
                .map(this::toSkillDomain).toList();

        return ProfessionalProfile.reconstitute(
                ProfileId.of(e.getId()),
                new FirebaseUid(e.getFirebaseUid()),
                e.getName() != null ? new ProfileName(e.getName()) : null,
                e.getSummary() != null ? new ProfessionalSummary(e.getSummary()) : null,
                e.getSalaryExpectation() != null ? new SalaryExpectation(e.getSalaryExpectation()) : null,
                e.getPreferredModality(),
                e.getProvenance(),
                e.getStatus(),
                e.getReviewStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                roles, exps, edus, skills);
    }

    private WorkExperience toWorkExpDomain(WorkExperienceEntity e) {
        return new WorkExperience(
                e.getId(), e.getCompany(), e.getPosition(), e.getDescription(),
                toYearMonth(e.getStartDate()),
                e.getEndDate() != null ? toYearMonth(e.getEndDate()) : null,
                e.getEmploymentStatus(), e.getSeniority(), e.getProvenance());
    }

    private Education toEducationDomain(EducationEntity e) {
        return new Education(
                e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                e.getLevel(),
                toYearMonth(e.getStartDate()),
                e.getEndDate() != null ? toYearMonth(e.getEndDate()) : null,
                e.isInProgress(), e.getProvenance());
    }

    private TargetRole toTargetRoleDomain(TargetRoleEntity e) {
        return new TargetRole(e.getId(), e.getRoleTitle(), e.getSeniority(), e.getProvenance());
    }

    private ProfileSkill toSkillDomain(ProfileSkillEntity e) {
        return new ProfileSkill(e.getId(), e.getSkillName(), e.getLevel(), e.getProvenance());
    }

    private static LocalDate toFirstDayOfMonth(YearMonth ym) {
        return ym.atDay(1);
    }

    private static YearMonth toYearMonth(LocalDate date) {
        return YearMonth.of(date.getYear(), date.getMonth());
    }
}
