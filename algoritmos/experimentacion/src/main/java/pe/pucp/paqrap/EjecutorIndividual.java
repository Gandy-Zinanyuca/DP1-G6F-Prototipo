package pe.pucp.paqrap;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import pe.pucp.paqrap.estricto.datos.DatasetLoader;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.ResultadoPlanificacion;
import pe.pucp.paqrap.estricto.servicios.EvaluadorFactibilidad;
import pe.pucp.paqrap.estricto.servicios.PlanificadorEstricto;

/** Carga, audita y presenta una ejecución individual con una salida breve y homogénea. */
final class EjecutorIndividual {
    record Entrada(DatasetLoader.Carga carga, int iteraciones, long semilla, long presupuestoMs) {}

    static Entrada cargar(String[] args, String clase) throws Exception {
        if (args.length < 4 || args.length > 7) {
            throw new IllegalArgumentException(
                    "Uso: " + clase + " ventas.AAAAMM.txt bloqueo.AAMM.txt "
                            + "mantenimiento.txt instante-ISO [iteraciones] [semilla] [presupuesto-ms]");
        }
        int iteraciones = args.length > 4 ? Integer.parseInt(args[4]) : 100;
        long semilla = args.length > 5 ? Long.parseLong(args[5]) : 20262L;
        long presupuestoMs = args.length > 6 ? Long.parseLong(args[6]) : 0;
        if (iteraciones < 0 || presupuestoMs < 0) {
            throw new IllegalArgumentException("Las iteraciones no pueden ser negativas");
        }
        var carga = new DatasetLoader().cargar(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]),
                LocalDateTime.parse(args[3]), 24, 400);
        return new Entrada(carga, iteraciones, semilla, presupuestoMs);
    }

    static void ejecutar(Entrada entrada, PlanificadorEstricto planificador) {
        ParametrosOperacion parametros = ParametrosOperacion.porDefecto();
        ResultadoPlanificacion resultado =
                planificador.planificar(entrada.carga().estado(), parametros);

        var auditoria = new EvaluadorFactibilidad(
                entrada.carga().estado(), parametros).evaluar(resultado.solucion());
        if (!auditoria.factible()
                || Math.abs(auditoria.objetivo() - resultado.metricas().objetivo()) > 1e-6) {
            throw new AssertionError("La auditoria independiente de la salida fallo");
        }

        var carga = entrada.carga();
        var m = resultado.metricas();
        System.out.printf(Locale.ROOT, "Instante=%s; presupuesto=%d ms; objetivo=%.2f%n",
                carga.estado().instante(), entrada.presupuestoMs(), m.objetivo());
        System.out.printf(Locale.ROOT,
                "%n%s%n"
                        + "Entrada: %d pedidos leidos; %d considerados; %d bloqueos; %d mantenimientos%n"
                        + "Busqueda: semilla=%d; iteraciones=%d; candidatos=%d; Ta=%.1f ms; parada=%s%n"
                        + "Cobertura: pedidos=%d/%d (%.1f%%); paquetes=%.1f%%; pendientes=%d%n"
                        + "Operacion: vehiculos=%d; utilizacion=%.1f%%; distancia=%.1f km; costo=S/ %.2f%n"
                        + "Resultado: rutas factibles=%s; demanda completa=%s%n",
                resultado.algoritmo(),
                carga.pedidosLeidos(), carga.estado().pedidos().size(),
                carga.bloqueosLeidos(), carga.estado().mantenimientos().size(),
                entrada.semilla(), m.iteraciones(), m.candidatosEvaluados(), m.taMs(), m.parada(),
                m.pedidosCompletos(), m.pedidosTotales(), 100 * m.cumplimientoPedidos(),
                100 * m.cumplimientoPaquetes(), m.paquetesPendientes(),
                m.vehiculosUsados(), 100 * m.utilizacionCapacidad(),
                m.distanciaKm(), m.costoOperacion(),
                auditoria.factible(), auditoria.completa());
    }

    private EjecutorIndividual() {}
}
