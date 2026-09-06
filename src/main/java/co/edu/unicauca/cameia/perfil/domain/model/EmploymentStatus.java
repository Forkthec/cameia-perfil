package co.edu.unicauca.cameia.perfil.domain.model;

/**
 * Estado de una experiencia laboral.
 *
 * <p>El prototipo de Frontend muestra dos checkboxes: "Trabajo aquí actualmente" y
 * "No recuerdo la fecha exacta de finalización". Eso corresponde a tres estados, no a un
 * booleano simple como el que tiene el C4 ({@code actual: boolean}):
 * <ul>
 *   <li>CURRENT — trabajo activo, sin fecha de fin.
 *   <li>UNKNOWN_END — trabajo finalizado pero la fecha exacta se desconoce.
 *   <li>ENDED — trabajo finalizado con fecha de fin conocida.
 * </ul>
 *
 * <p>Las reglas de negocio de {@code fechaFin} se validan en {@code WorkExperience}:
 * CURRENT y UNKNOWN_END no admiten fecha de fin; ENDED la requiere.
 */
public enum EmploymentStatus {
    CURRENT,
    UNKNOWN_END,
    ENDED
}
