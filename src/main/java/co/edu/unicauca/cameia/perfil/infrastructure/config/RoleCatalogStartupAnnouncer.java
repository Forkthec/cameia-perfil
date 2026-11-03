package co.edu.unicauca.cameia.perfil.infrastructure.config;

import co.edu.unicauca.cameia.perfil.application.service.RoleCatalogEventsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anuncia el catálogo de roles cada vez que la aplicación termina de arrancar.
 *
 * <p>Se apaga con {@code perfil.events.announce-role-catalog-on-startup=false}. Un fallo (por ejemplo, la base caída) se
 * registra como error y no tumba el arranque: la instantánea se anota en el siguiente arranque. Es un error y no un aviso
 * porque, mientras no se anote, Entrevista conserva un catálogo viejo (o vacío) sin que nada más lo diga.</p>
 */
@Component
@ConditionalOnProperty(name = "perfil.events.announce-role-catalog-on-startup", havingValue = "true", matchIfMissing = true)
public class RoleCatalogStartupAnnouncer {

    private static final Logger log = LoggerFactory.getLogger(RoleCatalogStartupAnnouncer.class);

    private final RoleCatalogEventsAppService service;

    RoleCatalogStartupAnnouncer(RoleCatalogEventsAppService service) {
        this.service = service;
    }

    /** Anota la instantánea del catálogo; nunca lanza una excepción hacia el arranque. */
    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        try {
            service.announceCatalog();
        } catch (RuntimeException failure) {
            // Sin los nombres de los roles ni el mensaje de la causa: puede traer valores de la base.
            log.error("No se pudo anotar el catálogo de roles al arrancar; Entrevista conserva el catálogo anterior hasta el próximo arranque [causa={}]",
                    failure.getClass().getSimpleName());
        }
    }
}
