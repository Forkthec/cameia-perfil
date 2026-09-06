package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
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

    /**
     * CM-16: crea un perfil vacío en estado IN_PROGRESS para el usuario identificado por su UID.
     * Un usuario con plan gratuito solo puede tener un perfil.
     * TODO CM-TBD Sprint 2: reemplazar existsByFirebaseUid por verificación contra CuotaPlanReplica.
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

    /** Carga un perfil por su id. Usado por CM-17 a CM-20. */
    public ProfessionalProfile loadProfile(UUID profileId) {
        return load(profileId);
    }

    private ProfessionalProfile load(UUID profileId) {
        return repository.findById(ProfileId.of(profileId))
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
    }
}
