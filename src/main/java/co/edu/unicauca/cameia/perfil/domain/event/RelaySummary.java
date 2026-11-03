package co.edu.unicauca.cameia.perfil.domain.event;

/**
 * Resultado de una corrida del relevo de la tabla de salida.
 *
 * @param published    eventos que el broker confirmó en la corrida
 * @param failed       intentos de publicación que no se confirmaron en la corrida
 * @param stillPending eventos que siguen pendientes al terminar la corrida
 */
public record RelaySummary(int published, int failed, long stillPending) {
}
