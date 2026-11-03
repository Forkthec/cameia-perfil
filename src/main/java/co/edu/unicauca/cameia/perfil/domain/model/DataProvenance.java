package co.edu.unicauca.cameia.perfil.domain.model;

/**
 * Procedencia de un dato dentro del perfil (glosario §6.5, C4 enum Procedencia).
 *
 * <p>Aplica campo a campo: un resumen puede ser AI_SUGGESTED mientras la experiencia es MANUAL.
 * Los códigos son el contrato con Frontend y el microservicio de Entrevista — NO se traducen.
 */
public enum DataProvenance {
    /** El candidato escribió el dato directamente. */
    MANUAL,
    /** La IA propuso el dato y el candidato no lo modificó. */
    AI_SUGGESTED,
    /** La IA propuso el dato y el candidato lo editó antes de guardar. */
    AI_EDITED
}
