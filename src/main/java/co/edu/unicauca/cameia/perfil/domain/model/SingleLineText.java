package co.edu.unicauca.cameia.perfil.domain.model;

import java.text.Normalizer;

/**
 * Texto de una línea tal como lo entiende el negocio: sin espacios en los extremos y en Unicode NFC,
 * para que el mismo texto escrito de dos formas sea el mismo valor.
 *
 * <p>«Espacio» es lo que quita {@code String.prototype.trim} de JavaScript en el navegador: los
 * caracteres de {@link Character#isWhitespace(int)}, los separadores de espacio de Unicode (incluido
 * el espacio duro U+00A0) y U+FEFF. El largo se cuenta en puntos de código, así que un carácter fuera
 * del plano básico (un emoji) cuenta como uno.</p>
 */
public final class SingleLineText {

    private static final int BYTE_ORDER_MARK = 0xFEFF;
    private static final int TAB = 0x09;
    private static final int LINE_FEED = 0x0A;
    private static final int CARRIAGE_RETURN = 0x0D;

    private SingleLineText() {
    }

    /**
     * Recorta y normaliza un texto recibido sin rechazar la ausencia.
     *
     * @param raw texto recibido; puede ser {@code null}
     * @return el texto recortado y en NFC, o {@code null} si {@code raw} es {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return trim(Normalizer.normalize(raw, Normalizer.Form.NFC));
    }

    /**
     * Igual que {@link #normalize(String)}, pero un resultado vacío pasa a {@code null}: es para los campos opcionales.
     *
     * @param raw texto recibido; puede ser {@code null}
     * @return el texto normalizado, o {@code null} si no queda nada
     */
    public static String normalizeOptional(String raw) {
        String normalized = normalize(raw);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /**
     * Largo del texto como lo cuenta el negocio.
     *
     * @param text texto ya normalizado; no {@code null}
     * @return cantidad de puntos de código
     */
    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * Indica si el texto tiene un carácter de control que el campo no admite.
     *
     * <p>Solo cuenta la categoría Unicode Cc (U+0000–U+001F y U+007F–U+009F): PostgreSQL rechaza
     * U+0000 y los demás son invisibles. Los de formato (Cf) se admiten a propósito: la unión de ancho
     * cero U+200D forma parte de emojis compuestos, como el de una familia.</p>
     *
     * @param text            texto ya normalizado; no {@code null}
     * @param allowLineBreaks {@code true} en los campos de varias líneas, que admiten tabulador, salto de línea y retorno
     * @return {@code true} si hay un carácter de control prohibido
     */
    public static boolean hasControlCharacter(String text, boolean allowLineBreaks) {
        return text.codePoints().anyMatch(cp -> Character.getType(cp) == Character.CONTROL
                && !(allowLineBreaks && isLineBreakOrTab(cp)));
    }

    private static boolean isLineBreakOrTab(int codePoint) {
        return codePoint == TAB || codePoint == LINE_FEED || codePoint == CARRIAGE_RETURN;
    }

    /** Recorta por puntos de código desde los dos extremos con la misma noción de espacio que JavaScript. */
    private static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    /** Espacio según {@code String.prototype.trim} de JavaScript. */
    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.getType(codePoint) == Character.SPACE_SEPARATOR
                || codePoint == BYTE_ORDER_MARK;
    }
}
