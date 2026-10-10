package co.edu.unicauca.cameia.perfil.domain.model;

import java.time.YearMonth;

/**
 * Datos recibidos para crear una experiencia laboral, ya convertidos a tipos del dominio. También
 * es la forma en que se reconstruye una fila guardada.
 *
 * @param company          empresa; puede ser {@code null} o en blanco (se informa como requerida)
 * @param position         cargo; puede ser {@code null} o en blanco
 * @param description      descripción opcional
 * @param startDate        mes de inicio opcional
 * @param endDate          mes de fin opcional
 * @param employmentStatus estado; puede ser {@code null} (se informa como requerido)
 */
public record WorkExperienceDraft(String company, String position, String description,
        YearMonth startDate, YearMonth endDate, EmploymentStatus employmentStatus) {
}
