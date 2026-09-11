package co.edu.unicauca.cameia.perfil.presentation.dto;

import java.util.List;

/** Cuerpo de respuesta 409 cuando el perfil no cumple los requisitos de completitud (CM-22). */
public record CompletionErrorResponse(List<String> missingRequirements) {}
