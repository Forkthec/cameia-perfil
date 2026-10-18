package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ProfileStatusInvalidException;

import java.util.Optional;

/**
 * Interpreta el filtro de estado de la lista de perfiles. Solo admite el nombre exacto de un estado
 * del ciclo de vida; cualquier otra cosa es un error del cliente, no «sin filtro».
 */
public final class ProfileStatusFilter {

    private ProfileStatusFilter() { }

    /**
     * Interpreta el valor de {@code ?status=}.
     *
     * @param raw valor de {@code ?status=}; {@code null} cuando no llegó
     * @return el estado pedido, o vacío si no hay filtro
     * @throws ProfileStatusInvalidException si llegó vacío, en minúsculas, desconocido o con caracteres extra
     */
    public static Optional<ProfileStatus> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (ProfileStatus status : ProfileStatus.values()) {
            if (status.name().equals(raw)) {
                return Optional.of(status);
            }
        }
        throw new ProfileStatusInvalidException();
    }
}
