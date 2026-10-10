/**
 * Restricciones de Bean Validation de los records de petición. Cada una ignora el valor ausente o en blanco, así que solo
 * {@code @NotBlank} informa la ausencia y cada campo tiene a lo sumo una violación.
 */
package co.edu.unicauca.cameia.perfil.presentation.dto.validation;
