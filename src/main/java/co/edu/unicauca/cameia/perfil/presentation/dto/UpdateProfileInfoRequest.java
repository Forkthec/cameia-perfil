package co.edu.unicauca.cameia.perfil.presentation.dto;

/**
 * Body del PATCH /api/v1/profiles/{id} (CM-17).
 * Todos los campos son opcionales: null significa "no modificar ese campo".
 */
public record UpdateProfileInfoRequest(
        String name,
        String summary,
        String preferredModality,
        String provenance) {
}
