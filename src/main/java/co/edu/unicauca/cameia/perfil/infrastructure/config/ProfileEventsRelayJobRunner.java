package co.edu.unicauca.cameia.perfil.infrastructure.config;

import co.edu.unicauca.cameia.perfil.application.service.OutboxRelayAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ejecuta una vez el relevo de eventos de Perfil: publica los pendientes de la tabla de salida.
 *
 * <p>Solo existe con el perfil {@code profile-events-relay}, que usa el Cloud Run Job programado cada 5 minutos; un
 * evento sin publicar no es una falla: espera la siguiente corrida.</p>
 */
@Component
@Profile(ProfileEventsRelayJobRunner.PROFILE)
public class ProfileEventsRelayJobRunner implements ApplicationRunner {

    /** Nombre del perfil de Spring que activa la tarea de relevo. */
    public static final String PROFILE = "profile-events-relay";

    private static final Logger log = LoggerFactory.getLogger(ProfileEventsRelayJobRunner.class);

    private final OutboxRelayAppService relayService;

    ProfileEventsRelayJobRunner(OutboxRelayAppService relayService) {
        this.relayService = relayService;
    }

    /**
     * Publica los pendientes y deja el resumen en el log.
     *
     * @param arguments argumentos de la aplicación; no se usan
     */
    @Override
    public void run(ApplicationArguments arguments) {
        var summary = relayService.relayPending();
        log.info("Relevo de eventos de Perfil terminado [publicados={}, fallidos={}, pendientes={}]",
                summary.published(), summary.failed(), summary.stillPending());
    }
}
