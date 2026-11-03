package co.edu.unicauca.cameia.perfil.domain.model;

import java.time.YearMonth;

/**
 * Datos recibidos para crear una formación académica, ya convertidos a tipos del dominio. También
 * es la forma en que se reconstruye una fila guardada.
 *
 * @param institution institución; puede ser {@code null} o en blanco (se informa como requerida)
 * @param degree      título obtenido; puede ser {@code null} o en blanco
 * @param level       nivel educativo; puede ser {@code null} (se informa como requerido)
 * @param startDate   mes de inicio; puede ser {@code null} (se informa como requerido)
 * @param endDate     mes de fin opcional; debe ser {@code null} si la formación está en curso
 * @param inProgress  si la formación sigue en curso
 */
public record EducationDraft(String institution, String degree, EducationLevel level,
        YearMonth startDate, YearMonth endDate, boolean inProgress) {
}
