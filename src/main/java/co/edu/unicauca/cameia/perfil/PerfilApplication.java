package co.edu.unicauca.cameia.perfil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del Microservicio de Perfil Profesional.
 *
 * <p>Este contexto es dueño de los Perfiles Profesionales, la experiencia, la educación, las
 * habilidades, los roles objetivo y la revisión humana de todo lo que sugiere la IA. No es dueño
 * de credenciales, pagos, sesiones de entrevista ni del ledger de consumo (glosario, sección 3).
 */
@SpringBootApplication
public class PerfilApplication {

    public static void main(String[] args) {
        SpringApplication.run(PerfilApplication.class, args);
    }
}
