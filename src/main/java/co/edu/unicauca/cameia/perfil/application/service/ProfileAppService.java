package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orquestador de los casos de uso del perfil profesional.
 * No contiene reglas de negocio: esas viven en {@link ProfessionalProfile}.
 * Inyecta el PUERTO de dominio, nunca el JpaRepository (ArchUnit lo verifica).
 */
@Service
@Transactional(readOnly = true)
public class ProfileAppService {

    private static final Logger log = LoggerFactory.getLogger(ProfileAppService.class);

    private final ProfessionalProfileRepository repository;

    ProfileAppService(ProfessionalProfileRepository repository) {
        this.repository = repository;
    }

    // ── CM-16 ────────────────────────────────────────────────────────────

    /**
     * CM-16: crea un perfil vacío en estado IN_PROGRESS.
     * TODO CM-TBD Sprint 2: reemplazar existsByFirebaseUid por CuotaPlanReplica.
     */
    @Transactional
    public ProfessionalProfile createProfile(CreateProfileCommand command) {
        var uid = new FirebaseUid(command.firebaseUid());
        if (repository.existsByFirebaseUid(uid)) {
            throw new ProfileAlreadyExistsException();
        }
        var profile = ProfessionalProfile.create(uid);
        repository.save(profile);
        log.info("perfil creado id={}", profile.getId().value());
        return profile;
    }

    // ── CM-17 ────────────────────────────────────────────────────────────

    /** CM-17: actualiza los campos opcionales de información general del perfil. */
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

    // ── Shared ────────────────────────────────────────────────────────────

    /** Carga un perfil por su id. Usado por CM-17 a CM-20. */
    public ProfessionalProfile loadProfile(UUID profileId) {
        return load(profileId);
    }

    private ProfessionalProfile load(UUID profileId) {
        return repository.findById(ProfileId.of(profileId))
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
    }
}
