package co.edu.unicauca.cameia.perfil;

import co.edu.unicauca.cameia.perfil.infrastructure.config.ProfileEventsRelayJobRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

import java.util.function.IntConsumer;

/**
 * Punto de entrada del Microservicio de Perfil Profesional.
 *
 * <p>Este contexto es dueño de los Perfiles Profesionales, la experiencia, la educación, las
 * habilidades, los roles objetivo y la revisión humana de todo lo que sugiere la IA. No es dueño
 * de credenciales, pagos, sesiones de entrevista ni del ledger de consumo (glosario, sección 3).
 */
@SpringBootApplication
public class PerfilApplication {

    /**
     * Arranca la aplicación y, si es una tarea de una sola ejecución, termina el proceso con su código de salida.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        exitWhenOneShotJob(SpringApplication.run(PerfilApplication.class, args), System::exit);
    }

    /**
     * Cierra el contexto y termina el proceso cuando está activo el perfil de una tarea de una sola ejecución.
     *
     * <p>Las tareas de una sola ejecución terminan con el código del contexto; los hilos de RabbitMQ mantendrían viva la
     * JVM, y una excepción al arrancar deja un código distinto de 0 para que el Job se reintente.</p>
     *
     * @param context contexto ya arrancado
     * @param exit    acción que termina el proceso con el código recibido
     */
    static void exitWhenOneShotJob(ConfigurableApplicationContext context, IntConsumer exit) {
        if (context.getEnvironment().acceptsProfiles(Profiles.of(ProfileEventsRelayJobRunner.PROFILE))) {
            exit.accept(SpringApplication.exit(context));
        }
    }
}
