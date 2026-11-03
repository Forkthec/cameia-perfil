package co.edu.unicauca.cameia.perfil.infrastructure.config;

import co.edu.unicauca.cameia.perfil.application.service.RoleCatalogEventsAppService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Anunciar el catálogo al arrancar nunca tumba el arranque y se puede apagar con la propiedad. */
@ExtendWith(MockitoExtension.class)
class RoleCatalogStartupAnnouncerTest {

    private static final String PROPERTY = "perfil.events.announce-role-catalog-on-startup";

    @Mock RoleCatalogEventsAppService service;

    @Test
    @DisplayName("Al estar la aplicación lista anota el catálogo")
    void onApplicationReady_shouldAnnounceCatalog_whenCalled() {
        new RoleCatalogStartupAnnouncer(service).onApplicationReady();

        verify(service).announceCatalog();
    }

    @Test
    @DisplayName("Si el servicio falla, la excepción no se propaga y el arranque sigue")
    void onApplicationReady_shouldNotPropagate_whenServiceFails() {
        doThrow(new DataAccessResourceFailureException("base caída")).when(service).announceCatalog();

        assertThatCode(() -> new RoleCatalogStartupAnnouncer(service).onApplicationReady()).doesNotThrowAnyException();
        verify(service).announceCatalog();
    }

    @Test
    @DisplayName("Si el servicio falla, se registra un ERROR con la clase de la causa y sin su mensaje")
    void onApplicationReady_shouldLogError_whenServiceFails() {
        var logger = (Logger) LoggerFactory.getLogger(RoleCatalogStartupAnnouncer.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new DataAccessResourceFailureException("valor-privado")).when(service).announceCatalog();

            new RoleCatalogStartupAnnouncer(service).onApplicationReady();

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("DataAccessResourceFailureException").doesNotContain("valor-privado");
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Con la propiedad en false el anunciador no existe")
    void announcer_shouldNotExist_whenPropertyIsFalse() {
        new ApplicationContextRunner().withBean(RoleCatalogEventsAppService.class, () -> mock(RoleCatalogEventsAppService.class))
                .withUserConfiguration(RoleCatalogStartupAnnouncer.class)
                .withPropertyValues(PROPERTY + "=false")
                .run(context -> assertThat(context).doesNotHaveBean(RoleCatalogStartupAnnouncer.class));
    }

    @Test
    @DisplayName("Con la propiedad en true, o sin ella, el anunciador existe")
    void announcer_shouldExist_whenPropertyIsTrueOrMissing() {
        var runner = new ApplicationContextRunner()
                .withBean(RoleCatalogEventsAppService.class, () -> mock(RoleCatalogEventsAppService.class))
                .withUserConfiguration(RoleCatalogStartupAnnouncer.class);

        runner.withPropertyValues(PROPERTY + "=true")
                .run(context -> assertThat(context).hasSingleBean(RoleCatalogStartupAnnouncer.class));
        runner.run(context -> assertThat(context).hasSingleBean(RoleCatalogStartupAnnouncer.class));
    }

    @Test
    @DisplayName("Con la configuración de la tarea de relevo la propiedad vale false y el anunciador no existe")
    void announcer_shouldNotExist_whenRelayProfileIsActive() {
        // Se carga solo el archivo del perfil, sin el de pruebas, que también apaga el anunciador y taparía un borrado.
        new ApplicationContextRunner().withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader()
                                .load("relay", new ClassPathResource("application-profile-events-relay.yml"))
                                .forEach(context.getEnvironment().getPropertySources()::addLast);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                })
                .withBean(RoleCatalogEventsAppService.class, () -> mock(RoleCatalogEventsAppService.class))
                .withUserConfiguration(RoleCatalogStartupAnnouncer.class)
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty(PROPERTY)).isEqualTo("false");
                    assertThat(context).doesNotHaveBean(RoleCatalogStartupAnnouncer.class);
                });
    }
}
