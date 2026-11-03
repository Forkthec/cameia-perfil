package co.edu.unicauca.cameia.perfil.domain.model;

/**
 * Estado de revisión humana de un perfil (C4 enum EstadoRevision, traducido a inglés per AGENTS §2).
 *
 * <p>C4 original usaba PENDIENTE/REVISADO (español); se adopta inglés para mantener coherencia
 * con los demás enums y con la decisión de idioma del equipo (AGENTS §2).
 * TODO CM-TBD-FRONT: confirmar con Frontend si el contrato de API usa PENDING_REVIEW o PENDIENTE.
 */
public enum ReviewStatus {
    PENDING_REVIEW,
    REVIEWED
}
