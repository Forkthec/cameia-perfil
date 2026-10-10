package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Nombre de una habilidad del perfil: de 1 a 60 caracteres tras recortar, contados en puntos de código en NFC.
 *
 * <p>El nombre se guarda como la persona lo escribió (recortado y en NFC). Dos nombres son la misma
 * habilidad si coincide su {@link #comparisonKey()}: las mayúsculas, las tildes y los espacios repetidos
 * no hacen distinta una habilidad. {@link #of(String)} aplica las reglas al texto recibido; el constructor
 * canónico solo rechaza {@code null} y reconstruye un nombre guardado sin volver a validar.</p>
 *
 * @param value el nombre tal como está guardado
 */
public record SkillName(String value) {

    /** Largo máximo del nombre, igual al de las columnas {@code nombre_habilidad} y {@code nombre_normalizado}. */
    public static final int MAX_LENGTH = 60;

    /** Rechazo de un nombre con un carácter de control. */
    public static final FieldError CHARACTERS = new FieldError(
            "skillName", ErrorCode.SKILL_NAME_INVALID_CHARACTERS, "La habilidad tiene caracteres no permitidos.");

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    /**
     * Reconstruye un nombre ya guardado.
     *
     * @param value nombre tal como está guardado; no {@code null}
     */
    public SkillName {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Crea el nombre a partir del texto recibido, aplicando todas sus reglas.
     *
     * <p>Gana la primera regla que falla: obligatorio, caracteres de control y largo, en ese orden.</p>
     *
     * @param raw nombre recibido; no {@code null}
     * @return el nombre recortado y en NFC
     * @throws InvalidFieldsException con {@code SKILL_NAME_REQUIRED}, {@code SKILL_NAME_INVALID_CHARACTERS}
     *                                o {@code SKILL_NAME_TOO_LONG}
     */
    public static SkillName of(String raw) {
        var text = SingleLineText.normalize(Objects.requireNonNull(raw, "raw"));
        if (text.isEmpty()) {
            throw InvalidFieldsException.of("skillName", ErrorCode.SKILL_NAME_REQUIRED, "Ingresa una habilidad.");
        }
        if (SingleLineText.hasControlCharacter(text, false)) {
            throw InvalidFieldsException.of(CHARACTERS);
        }
        if (SingleLineText.length(text) > MAX_LENGTH) {
            throw InvalidFieldsException.of("skillName", ErrorCode.SKILL_NAME_TOO_LONG,
                    "La habilidad no puede superar los " + MAX_LENGTH + " caracteres.");
        }
        return new SkillName(text);
    }

    /**
     * Clave para detectar habilidades repetidas: espacios unidos, minúsculas en {@link Locale#ROOT} y sin marcas
     * combinantes tras la descomposición canónica, así «Programación» y «programacion» —y «Diseño» y «Diseno»—
     * son la misma habilidad.
     *
     * @return la clave de comparación, de a lo sumo {@link #MAX_LENGTH} puntos de código
     */
    public String comparisonKey() {
        var lower = SingleLineText.collapseSpaces(value).toLowerCase(Locale.ROOT);
        var withoutMarks = COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        return Normalizer.normalize(withoutMarks, Normalizer.Form.NFC);
    }

    /**
     * Indica si dos nombres son la misma habilidad.
     *
     * @param other otro nombre; no {@code null}
     * @return {@code true} si coinciden sus claves de comparación
     */
    public boolean sameAs(SkillName other) {
        return comparisonKey().equals(other.comparisonKey());
    }
}
