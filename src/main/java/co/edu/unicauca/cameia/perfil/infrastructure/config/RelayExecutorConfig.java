package co.edu.unicauca.cameia.perfil.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ejecutor del intento de publicación posterior al commit.
 *
 * <p>Corre fuera del hilo de la petición porque, al terminar el commit, la conexión de la petición sigue tomada: anotar
 * el resultado desde ese mismo hilo pediría una segunda conexión y, con el pool lleno, todas las peticiones esperarían
 * el plazo completo. Es acotado a propósito: con la cola llena rechaza el intento y la fila queda pendiente para el
 * Job, en vez de acumular memoria o frenar la petición.</p>
 */
@Configuration
public class RelayExecutorConfig {

    static final int THREADS = 2;
    static final int QUEUE_CAPACITY = 100;

    /** @return ejecutor de {@value #THREADS} hilos y cola de {@value #QUEUE_CAPACITY} intentos, que rechaza al llenarse */
    @Bean(name = "profileEventsRelayExecutor", destroyMethod = "shutdown")
    public Executor profileEventsRelayExecutor() {
        var counter = new AtomicInteger();
        return new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
                    // Hilo de servicio: no impide apagar la JVM; lo que no alcance a salir lo recoge el Job.
                    var thread = new Thread(task, "perfil-evento-relevo-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }
}
