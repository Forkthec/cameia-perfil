package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el Perfil Profesional del Usuario respetando el cupo de su plan.
 *
 * <p>Varias peticiones del mismo Usuario pueden llegar a la vez (doble clic): un bloqueo por Usuario
 * las ordena para que nunca se cree más de un perfil por encima del cupo.</p>
 */
@Service
public class ProfileCreationAppService {

    /** Cantidad máxima de Perfiles Profesionales del Plan Free. */
    static final int FREE_PLAN_MAX_PROFILES = 1;

    private static final Logger log = LoggerFactory.getLogger(ProfileCreationAppService.class);
    private final ProfessionalProfileRepository repository;

    ProfileCreationAppService(ProfessionalProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Crea el Perfil Profesional vacío del Usuario.
     *
     * <p>Si llega otra petición del mismo Usuario mientras una creación está en proceso, espera a que
     * termine y devuelve el perfil que esa creó, en vez de crear otro o rechazarla. Si el Usuario ya
     * tenía el máximo de perfiles antes de pedir, rechaza la creación.</p>
     *
     * @param command identidad del Usuario
     * @return el perfil creado, o el que creó la petición que estaba en proceso
     * @throws IdentityRequiredException    si la identidad falta o no es válida
     * @throws ProfileLimitReachedException si el Usuario ya tenía el máximo de perfiles de su plan
     */
    @Transactional
    public ProfessionalProfile createProfile(CreateProfileCommand command) {
        var uid = FirebaseUid.required(command.firebaseUid());
        long before = repository.countByFirebaseUid(uid);
        // Serializa las creaciones del mismo Usuario: el segundo conteo ve lo que confirmó la anterior.
        repository.lockCreationFor(uid);
        long after = repository.countByFirebaseUid(uid);
        if (after < FREE_PLAN_MAX_PROFILES) return createEmpty(uid);
        // Solo falla si la base se contradice (cuenta un perfil y no lo encuentra): cae en el 500 genérico.
        if (before < FREE_PLAN_MAX_PROFILES) return repository.findLatestByFirebaseUid(uid).orElseThrow();
        throw new ProfileLimitReachedException();
    }

    private ProfessionalProfile createEmpty(FirebaseUid uid) {
        var profile = ProfessionalProfile.create(uid);
        repository.save(profile);
        log.info("perfil creado id={}", profile.getId().value());
        return profile;
    }
}
