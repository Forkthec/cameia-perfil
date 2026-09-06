package co.edu.unicauca.cameia.perfil.domain.model;

/**
 * Ciclo de vida de un perfil profesional (glosario §6.5).
 *
 * <p>Transiciones válidas:
 * <ul>
 *   <li>PENDING → IN_PROGRESS (primer guardado parcial)
 *   <li>IN_PROGRESS → IN_REVIEW (candidato solicita revisión humana)
 *   <li>IN_REVIEW → COMPLETED (revisor aprueba) o IN_PROGRESS (revisor devuelve)
 * </ul>
 *
 * <p>PENDING existe para perfiles creados por el sistema (ej. importación futura);
 * los creados vía POST /profiles arrancan directamente en IN_PROGRESS.
 * TODO CM-TBD-PO: confirmar si PENDING se usará en Sprint 1 o solo en Sprint 2.
 */
public enum ProfileStatus {
    PENDING,
    IN_PROGRESS,
    IN_REVIEW,
    COMPLETED
}
