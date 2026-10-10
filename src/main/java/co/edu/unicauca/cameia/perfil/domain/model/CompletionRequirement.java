package co.edu.unicauca.cameia.perfil.domain.model;

/**
 * Requisito que un perfil debe cumplir para pasar a Activo, declarado en el orden en que lo listan los criterios de
 * aceptación; los que faltan se informan en ese mismo orden.
 */
public enum CompletionRequirement {
    /** El perfil tiene un nombre que no está en blanco. */
    NAME,
    /** El resumen existe, no está en blanco y no supera el máximo del resumen. */
    SUMMARY,
    /** Al menos una formación académica. */
    EDUCATION,
    /** Al menos una habilidad. */
    SKILLS,
    /** Al menos un rol objetivo. */
    TARGET_ROLES
}
