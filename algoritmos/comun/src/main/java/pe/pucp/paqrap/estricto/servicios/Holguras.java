package pe.pucp.paqrap.estricto.servicios;

import java.time.*;
import java.util.*;
import pe.pucp.paqrap.estricto.modelo.*;

/**
 * Una observacion por pedido completo: deadline menos fin de servicio de su
 * ultima parte.
 */
public final class Holguras {
    public record Resumen(int completos, Double promedioMin, Double minimaMin) {
    }

    public static Resumen calcular(List<Pedido> pedidos, List<ResultadoRuta> rutas) {
        var cantidades = new HashMap<String, Integer>();
        var finales = new HashMap<String, LocalDateTime>();
        for (var ruta : rutas)
            if (ruta.factible())
                for (var parada : ruta.paradas())
                    for (var parte : parada.partes()) {
                        cantidades.merge(parte.pedido().id(), parte.cantidad(), Math::addExact);
                        finales.merge(parte.pedido().id(), parada.finServicio(), (a, b) -> a.isAfter(b) ? a : b);
                    }
        int n = 0;
        double suma = 0, minima = Double.POSITIVE_INFINITY;
        for (var p : pedidos)
            if (cantidades.getOrDefault(p.id(), 0) == p.cantidad()) {
                double margen = Duration.between(finales.get(p.id()), p.deadline()).toMillis() / 60000.0;
                suma += margen;
                minima = Math.min(minima, margen);
                n++;
            }
        return new Resumen(n, n == 0 ? null : suma / n, n == 0 ? null : minima);
    }

    private Holguras() {
    }
}
