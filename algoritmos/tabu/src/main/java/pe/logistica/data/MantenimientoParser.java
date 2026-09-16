package pe.logistica.data;

import pe.logistica.model.Mantenimiento;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class MantenimientoParser {
    public Mantenimiento parsear(String linea) {
        String[] partes = linea.trim().split(":", -1);
        if (partes.length != 2 || !partes[0].trim().matches("[0-9]{8}"))
            throw new IllegalArgumentException("Mantenimiento: se esperaba AAAAMMDD:TTNN");
        return new Mantenimiento(LocalDate.parse(partes[0].trim(), DateTimeFormatter.BASIC_ISO_DATE), partes[1].trim());
    }
    public List<Mantenimiento> leer(Path archivo) throws IOException {
        return ParserSupport.leer(archivo, this::parsear);
    }
    /** Patron de dos meses repetido conservando el dia y la paridad del mes. */
    public List<Mantenimiento> expandirBimensual(List<Mantenimiento> base, int desde, int hasta) {
        if (base.isEmpty() || desde > hasta) throw new IllegalArgumentException("Patron o periodo de mantenimiento invalido");
        YearMonth primero = base.stream().map(m -> YearMonth.from(m.fecha())).min(Comparator.naturalOrder()).orElseThrow();
        if (base.stream().anyMatch(m -> ChronoUnit.MONTHS.between(primero, YearMonth.from(m.fecha())) > 1))
            throw new IllegalArgumentException("El patron debe abarcar solo dos meses consecutivos");
        var resultado = new ArrayList<Mantenimiento>();
        for (int anio = desde; anio <= hasta; anio++) for (int mes = 1; mes <= 12; mes++) {
            YearMonth destino = YearMonth.of(anio, mes);
            for (Mantenimiento m : base) {
                if (Math.floorMod(ChronoUnit.MONTHS.between(YearMonth.from(m.fecha()), destino), 2) == 0)
                    resultado.add(new Mantenimiento(destino.atDay(m.fecha().getDayOfMonth()), m.codigoVehiculo()));
            }
        }
        return resultado.stream().distinct().sorted(Comparator.comparing(Mantenimiento::fecha)
                .thenComparing(Mantenimiento::codigoVehiculo)).toList();
    }
}
