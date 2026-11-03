package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;

import java.util.UUID;

/**
 * Rol del catálogo de roles profesionales TI (glosario: Rol profesional).
 *
 * <p>Solo un rol activo se puede elegir como Rol Objetivo. Un rol inactivo sigue existiendo porque los Roles Objetivo
 * que ya lo usan lo referencian, pero no se ofrece ni se acepta en una elección nueva.</p>
 *
 * @param id        identificador del rol en el catálogo
 * @param nombre    nombre visible en el idioma pedido
 * @param categoria categoría visible en el idioma pedido
 * @param active    {@code true} si el catálogo lo ofrece hoy
 */
public record ProfessionalRole(UUID id, String nombre, String categoria, boolean active) {

    /**
     * Confirma que el rol se puede elegir como Rol Objetivo.
     *
     * @return este mismo rol, para encadenar
     * @throws InvalidFieldsException si el rol no está activo: 422 con el campo {@code professionalRoleId}
     */
    public ProfessionalRole requireSelectable() {
        if (!active) {
            throw InvalidFieldsException.of("professionalRoleId", ErrorCode.PROFESSIONAL_ROLE_DISABLED, ProfessionalRoleNotFoundException.MESSAGE);
        }
        return this;
    }
}
