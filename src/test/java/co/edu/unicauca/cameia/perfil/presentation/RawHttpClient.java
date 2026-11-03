package co.edu.unicauca.cameia.perfil.presentation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente HTTP de pruebas que escribe la petición byte a byte en un socket. Existe porque
 * {@code java.net.URI} rechaza {@code %zz} y {@code HttpClient} normaliza los encabezados:
 * aquí la petición mala llega tal cual al servidor.
 */
final class RawHttpClient {

    /** Respuesta reducida a lo que las pruebas comprueban. */
    record Reply(int status, Map<String, String> headers, String body) { }

    private static final int TIMEOUT_MILLIS = 10_000;

    private final int port;

    RawHttpClient(int port) {
        this.port = port;
    }

    /**
     * Envía una petición y lee la respuesta completa.
     *
     * @param method  método HTTP, por ejemplo {@code PATCH}
     * @param target  ruta y consulta tal como se escriben en la línea de petición, sin validar
     * @param headers encabezados completos {@code Nombre: valor}; se escriben en ISO-8859-1
     * @param body    cuerpo (cada carácter es un byte) o {@code null}; con un encabezado
     *                {@code Transfer-Encoding} se escribe tal cual, sin {@code Content-Length}
     * @return la respuesta con el cuerpo ya decodificado en UTF-8
     * @throws IOException si el socket falla
     */
    Reply send(String method, String target, List<String> headers, String body) throws IOException {
        var payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.ISO_8859_1);
        var head = new StringBuilder(method).append(' ').append(target).append(" HTTP/1.1\r\n")
                .append("Host: localhost\r\nConnection: close\r\n");
        headers.forEach(header -> head.append(header).append("\r\n"));
        var chunked = headers.stream().anyMatch(h -> h.toLowerCase(Locale.ROOT).startsWith("transfer-encoding:"));
        if (body != null && !chunked) {
            head.append("Content-Length: ").append(payload.length).append("\r\n");
        }
        head.append("\r\n");
        try (var socket = new Socket("localhost", port)) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            // La respuesta se lee mientras se escribe: ante un cuerpo grande el servidor contesta y cierra la
            // conexión sin leerlo entero, y el cierre descartaría lo que no se hubiera leído.
            var response = CompletableFuture.supplyAsync(() -> readAll(socket));
            var out = socket.getOutputStream();
            try {
                out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
                out.write(payload);
                out.flush();
            } catch (SocketException serverClosedEarly) {
                // El servidor ya respondió y cerró; lo que importa es la respuesta que se leyó.
            }
            return parse(response.join());
        }
    }

    private static String readAll(Socket socket) {
        var buffer = new ByteArrayOutputStream();
        var chunk = new byte[8192];
        try {
            var in = socket.getInputStream();
            for (var read = in.read(chunk); read >= 0; read = in.read(chunk)) {
                buffer.write(chunk, 0, read);
            }
        } catch (SocketException reset) {
            // El servidor cerró con la conexión a medias: vale lo ya leído.
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        // ISO-8859-1 conserva un carácter por byte: los largos de los fragmentos son en bytes.
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    /** Separa línea de estado, encabezados (claves en minúsculas) y cuerpo; decodifica {@code chunked}. */
    private static Reply parse(String raw) {
        var split = raw.indexOf("\r\n\r\n");
        var headPart = raw.substring(0, split);
        var bodyPart = raw.substring(split + 4);
        var lines = headPart.split("\r\n");
        var status = Integer.parseInt(lines[0].split(" ")[1]);
        var headers = new HashMap<String, String>();
        for (var i = 1; i < lines.length; i++) {
            var colon = lines[i].indexOf(':');
            headers.put(lines[i].substring(0, colon).toLowerCase(), lines[i].substring(colon + 1).trim());
        }
        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            bodyPart = dechunk(bodyPart);
        }
        // Los largos de los fragmentos se cuentan en bytes: la respuesta se leyó como ISO-8859-1 y aquí se pasa a UTF-8.
        var utf8 = new String(bodyPart.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return new Reply(status, headers, utf8);
    }

    private static String dechunk(String chunked) {
        var out = new StringBuilder();
        var pos = 0;
        while (true) {
            var eol = chunked.indexOf("\r\n", pos);
            var size = Integer.parseInt(chunked.substring(pos, eol).split(";")[0].trim(), 16);
            if (size == 0) {
                return out.toString();
            }
            out.append(chunked, eol + 2, eol + 2 + size);
            pos = eol + 2 + size + 2;
        }
    }
}
