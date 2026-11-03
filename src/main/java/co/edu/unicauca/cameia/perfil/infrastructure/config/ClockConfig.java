package co.edu.unicauca.cameia.perfil.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Reloj del servicio: las fechas se calculan en UTC y las pruebas reemplazan el reloj por uno fijo. */
@Configuration
public class ClockConfig {

    /** @return el reloj del sistema en UTC */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
