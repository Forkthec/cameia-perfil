package co.edu.unicauca.cameia.perfil.domain.port;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Copia local de la fecha de nacimiento de cada Usuario, alimentada por los eventos de cuenta del servicio de cuentas.
 *
 * <p>Las reglas de fechas de la experiencia laboral y la formación la leen en lugar de llamar a otro servicio. La falta de la
 * fila significa que el evento de cuenta creada aún no se procesó, no que el Usuario no tenga fecha de nacimiento.</p>
 */
public interface BirthDateReplica {

    /**
     * Lee la fecha de nacimiento replicada.
     *
     * @param userId identidad del Usuario
     * @return la fecha de nacimiento replicada, o vacío si aún no llegó
     */
    Optional<LocalDate> findBirthDate(FirebaseUid userId);

    /**
     * Guarda la fecha de nacimiento si el Usuario aún no tiene una.
     *
     * @param userId    identidad del Usuario
     * @param birthDate fecha de nacimiento
     * @return true si se insertó una fila
     */
    boolean saveIfAbsent(FirebaseUid userId, LocalDate birthDate);

    /**
     * Borra la copia del Usuario.
     *
     * @param userId identidad del Usuario
     * @return true si se borró una fila
     */
    boolean delete(FirebaseUid userId);
}
