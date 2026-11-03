package co.edu.unicauca.cameia.perfil.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Rol del catálogo con sus dos idiomas y su estado (glosario: Rol profesional), tal como se anuncia a los demás servicios.
 *
 * <p>A diferencia de {@link ProfessionalRole}, que muestra un solo idioma y solo se ofrece si está activo, este valor
 * lleva los dos nombres y también los roles inactivos: quien mantiene una copia del catálogo necesita saber cuáles se
 * desactivaron. El nombre en inglés nunca queda vacío: si falta, se usa el español, para que la instantánea del catálogo
 * no viole su propio contrato.</p>
 *
 * @param id     identificador del rol en el catálogo
 * @param nameEs nombre en español, no vacío
 * @param nameEn nombre en inglés; si llega nulo o en blanco se usa {@code nameEs}
 * @param active {@code true} si el catálogo ofrece hoy el rol
 */
public record ProfessionalRoleNames(UUID id, String nameEs, String nameEn, boolean active) {

    /** Largo máximo, en puntos de código, de cada nombre: el de las columnas {@code nombre} y {@code nombre_en}. */
    public static final int MAX_NAME_LENGTH = 255;

    /**
     * Comprueba los componentes y completa el nombre en inglés.
     *
     * @throws NullPointerException     si falta el identificador
     * @throws IllegalArgumentException si el nombre en español es nulo o está en blanco, o si algún nombre pasa de
     *                                  {@link #MAX_NAME_LENGTH} puntos de código
     */
    public ProfessionalRoleNames {
        Objects.requireNonNull(id, "id");
        if (nameEs == null || nameEs.isBlank()) {
            throw new IllegalArgumentException("El rol del catálogo debe tener nombre en español");
        }
        if (nameEn == null || nameEn.isBlank()) {
            nameEn = nameEs;
        }
        if (exceedsMaximum(nameEs) || exceedsMaximum(nameEn)) {
            throw new IllegalArgumentException("El nombre del rol del catálogo no puede pasar de " + MAX_NAME_LENGTH + " caracteres");
        }
    }

    private static boolean exceedsMaximum(String name) {
        return name.codePointCount(0, name.length()) > MAX_NAME_LENGTH;
    }
}
