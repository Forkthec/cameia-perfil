package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.UUID;

/** Rol del catálogo centralizado de roles profesionales TI (CM-23). */
public record ProfessionalRole(UUID id, String nombre, String categoria) {}
