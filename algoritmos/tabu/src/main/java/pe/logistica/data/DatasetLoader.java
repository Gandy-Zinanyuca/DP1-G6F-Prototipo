package pe.logistica.data;

import pe.logistica.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Carga los TXT publicados, sin dependencias de Downloads ni de una BD. */
public final class DatasetLoader {
    public record Datos(List<Pedido> pedidos, List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos) { }
    private static final Pattern ARCHIVO_BLOQUEO = Pattern.compile("bloqueo\\.(\\d{2})(\\d{2})\\.txt");
    public Datos cargar(Path raiz, LocalDateTime t, long scMinutos) throws IOException {
        var pedidos = new ArrayList<Pedido>();
        LocalDateTime fin = t.plusMinutes(scMinutos);
        for (YearMonth mes = YearMonth.from(t); !mes.isAfter(YearMonth.from(fin)); mes = mes.plusMonths(1)) {
            String archivo = String.format(Locale.ROOT, "ventas.%04d%02d.txt", mes.getYear(), mes.getMonthValue());
            var leidos = new PedidoParser().leer(raiz.resolve("ventas").resolve(archivo), mes.getYear(), mes.getMonthValue());
            leidos.stream().filter(p -> !p.fechaRegistro().isBefore(t) && !p.fechaRegistro().isAfter(fin)).forEach(pedidos::add);
        }
        var bloqueos = new ArrayList<Bloqueo>();
        try (var archivos = Files.list(raiz.resolve("bloqueos"))) {
            for (Path archivo : archivos.filter(Files::isRegularFile).sorted().toList()) {
                var match = ARCHIVO_BLOQUEO.matcher(archivo.getFileName().toString());
                if (!match.matches()) continue;
                int anio = 2000 + Integer.parseInt(match.group(1)), mes = Integer.parseInt(match.group(2));
                for (Bloqueo b : new BloqueoParser().leer(archivo, anio, mes))
                    if (b.fin().isAfter(t)) bloqueos.add(b);
            }
        }
        var parser = new MantenimientoParser();
        var base = parser.leer(raiz.resolve("mant.preventivo.09.10.txt"));
        return new Datos(List.copyOf(pedidos), List.copyOf(bloqueos), parser.expandirBimensual(base, 2026, 2029));
    }
}
