package pe.pucp.paqrap;

import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import pe.pucp.paqrap.estricto.datos.*;
import pe.pucp.paqrap.tabu.*;
import pe.pucp.paqrap.alns.estricto.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Diagnostico de solo lectura: no modifica ni depende de archivos de la
 * campana en curso. Toma UNA foto realista del estado (pedidos reales de los
 * primeros dias de un mes, flota completa disponible) y llama a
 * motor.planificar() directamente con distintos limites de iteraciones, para
 * ver cuanto cambia la solucion (objetivo) al recortar el presupuesto de
 * busqueda. No corre la simulacion completa de 4320 ciclos.
 */
public final class DiagnosticoConvergencia {

    private static EstadoOperacion cargarBase(Path ventas, Path bloqueos, YearMonth mes) throws Exception {
        var inicio = mes.atDay(1).atStartOfDay();
        Path v = ventas.resolve("ventas." + mes.toString().replace("-", "") + ".txt");
        Path b = bloqueos.resolve(
                String.format(Locale.ROOT, "bloqueo.%02d%02d.txt", mes.getYear() % 100, mes.getMonthValue()));
        var base = new DatasetLoader().cargarExperimental(v, b, inicio, 48, Integer.MAX_VALUE).estado();
        return base;
    }

    public static void main(String[] args) throws Exception {
        Path ventas = Path.of("algoritmos/alns/data/ventas.v20260909");
        Path bloqueos = Path.of("algoritmos/alns/data/bloqueos.v20260909");
        YearMonth mes = YearMonth.parse(args.length > 0 ? args[0] : "2026-02");
        int diasBacklog = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        long semilla = args.length > 2 ? Long.parseLong(args[2]) : 20262;

        var base = cargarBase(ventas, bloqueos, mes);
        var pedidosMes = new PedidoParser().leer(
                ventas.resolve("ventas." + mes.toString().replace("-", "") + ".txt"), mes.getYear(),
                mes.getMonthValue());
        var inicio = mes.atDay(1).atStartOfDay();
        var corte = inicio.plusDays(diasBacklog);
        var pendientes = pedidosMes.stream().filter(p -> !p.fechaRegistro().isAfter(corte))
                .sorted(Comparator.comparing(Pedido::fechaRegistro).thenComparing(Pedido::id)).toList();

        System.out.println("Mes=" + mes + " backlog=" + diasBacklog + "d pedidos_pendientes=" + pendientes.size()
                + " vehiculos=" + base.vehiculos().size() + " semilla=" + semilla);

        var estado = new EstadoOperacion(corte, pendientes, base.vehiculos(), base.almacenes(), base.bloqueos(),
                List.of(), List.of(), List.of(), Set.of());
        var parametros = ParametrosOperacion.porDefecto();

        int[] iteraciones = { 50, 100, 150, 200, 250, 300 };
        System.out.println();
        System.out.println("algoritmo,iter_max,objetivo,pedidos_completos,pedidos_totales,distancia_km,"
                + "vehiculos_usados,iteracion_mejor,iteraciones_reales,Ta_ms");
        for (String algo : List.of("TS", "ALNS")) {
            for (int iter : iteraciones) {
                PlanificadorEstricto motor = algo.equals("TS")
                        ? new TabuSearchPlanner(new ConfiguracionTabu(iter, 7, Math.max(5, iter / 10), 400, 0, semilla))
                        : new ALNSPlanner(new ConfiguracionALNS(iter, Math.max(1, iter), 4, 5, .7, .05, 0, semilla));
                long t0 = System.nanoTime();
                var r = motor.planificar(estado, parametros);
                double taMs = (System.nanoTime() - t0) / 1e6;
                var m = r.metricas();
                System.out.printf(Locale.ROOT, "%s,%d,%.6f,%d,%d,%.3f,%d,%d,%d,%.1f%n", algo, iter, m.objetivo(),
                        m.pedidosCompletos(), m.pedidosTotales(), m.distanciaKm(), m.vehiculosUsados(),
                        m.iteracionMejor(), m.iteraciones(), taMs);
            }
        }
    }
}
