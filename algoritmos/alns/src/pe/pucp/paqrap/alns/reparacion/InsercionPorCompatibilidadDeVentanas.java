package pe.pucp.paqrap.alns.reparacion;

import pe.pucp.paqrap.alns.CacheInserciones;
import pe.pucp.paqrap.alns.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.alns.OperadorReparacion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Inserción por compatibilidad de ventanas de tiempo.
 *
 * <p>Los dos reparadores anteriores ordenan por costo. Este ordena por <b>riesgo temporal</b>,
 * que en PaqRap es la magnitud que decide si el escenario sobrevive: como una sola entrega
 * fuera de plazo constituye colapso logístico, ahorrar soles es irrelevante frente a preservar
 * margen de maniobra.</p>
 *
 * <p>El procedimiento tiene dos niveles:</p>
 * <ol>
 *   <li><b>Orden de colocación</b>: los pedidos se insertan en orden creciente de holgura
 *       —minutos restantes hasta su hora límite—, de modo que los priorizados de 4 horas y los
 *       regulares próximos a vencer eligen sitio antes que el resto (LE096, LE097).</li>
 *   <li><b>Elección de unidad</b>: entre las inserciones admisibles se prefiere la que deje
 *       <b>mayor holgura mínima</b> en la ruta resultante, no la más barata. Colocar un pedido
 *       en la unidad que llega con más margen conserva capacidad de absorber bloqueos y averías
 *       que aparezcan en los ciclos siguientes.</li>
 * </ol>
 *
 * <p>El costo monetario entra solo como criterio de desempate, lo que convierte a este operador
 * en el complemento natural de los dos anteriores: aporta robustez donde ellos aportan
 * eficiencia, y el mecanismo adaptativo decide cuál conviene según la fase de la operación.</p>
 */
public class InsercionPorCompatibilidadDeVentanas implements OperadorReparacion {

    @Override
    public void reparar(Solucion solucion, List<Pedido> porInsertar,
                        ContextoPlanificacion ctx, Random aleatorio) {
        final int ahora = ctx.getMinutoActual();
        CacheInserciones cache = new CacheInserciones(solucion, ctx, porInsertar, true);

        while (!cache.vacia()) {
            List<Pedido> pendientes = cache.pendientes();
            pendientes.sort(Comparator.comparingInt(p -> p.holgura(ahora)));

            Pedido elegido = null;
            for (Pedido p : pendientes) {
                if (cache.mejor(p) == null) {
                    cache.descartar(p);
                    solucion.marcarNoAsignado(p);
                    continue;
                }
                elegido = p;
                break;
            }
            if (elegido == null) {
                break;
            }
            Insercion mejor = seleccionarPorHolgura(cache, elegido);
            if (mejor == null || !cache.aplicar(elegido, mejor)) {
                continue;
            }
        }
        for (Pedido p : cache.pendientes()) {
            solucion.marcarNoAsignado(p);
        }
    }

    /**
     * Entre las alternativas registradas para el pedido, elige la que deje mayor holgura mínima
     * en la ruta; a igualdad de holgura, la de menor costo de inserción.
     */
    private Insercion seleccionarPorHolgura(CacheInserciones cache, Pedido p) {
        Insercion mejor = null;
        for (Insercion ins : cache.alternativas(p)) {
            if (mejor == null
                    || ins.holguraResultante > mejor.holguraResultante
                    || (ins.holguraResultante == mejor.holguraResultante && ins.delta < mejor.delta)) {
                mejor = ins;
            }
        }
        return mejor;
    }

    @Override
    public String nombre() {
        return "insercion-compatibilidad-ventanas";
    }
}
