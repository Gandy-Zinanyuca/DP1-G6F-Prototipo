package pe.pucp.paqrap.estricto.datos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

final class ParserSupport {
    private static final Pattern MOMENTO = Pattern.compile("(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m");
    private ParserSupport() { }
    static LocalDateTime momento(String texto, int anio, int mes) {
        var m = MOMENTO.matcher(texto.trim());
        if (!m.matches()) throw new IllegalArgumentException("Fecha mensual invalida: " + texto);
        return YearMonth.of(anio, mes).atDay(Integer.parseInt(m.group(1)))
                .atTime(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }
    static <T> List<T> leer(Path archivo, Function<String, T> parser) throws IOException {
        return leerConLinea(archivo, (s, numero) -> parser.apply(s));
    }
    static <T> List<T> leerConLinea(Path archivo, BiFunction<String, Integer, T> parser) throws IOException {
        List<T> resultado = new ArrayList<>();
        int numero = 0;
        for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            numero++;
            String limpia = linea.replace("\uFEFF", "").trim();
            if (limpia.isEmpty() || limpia.startsWith("#")) continue;
            try { resultado.add(parser.apply(limpia, numero)); }
            catch (RuntimeException e) { throw new IllegalArgumentException(archivo + ":" + numero + ": " + e.getMessage(), e); }
        }
        return List.copyOf(resultado);
    }
}
