package pe.pucp.paqrap.alns.reparacion;

import pe.pucp.paqrap.alns.CacheInserciones;
import pe.pucp.paqrap.alns.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.alns.OperadorReparacion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.List;
import java.util.Random;

/**
 * Inserción por arrepentimiento de orden k (<i>regret-k insertion</i>).
 *
 * <p>Corrige la miopía del reparador goloso incorporando una mirada hacia adelante. Para cada
 * pedido pendiente se calcula su <b>arrepentimiento</b>: cuánto se perdería si no se lo coloca
 * ahora en su mejor unidad y hubiera que usar la segunda, la tercera, …, la k-ésima mejor.</p>
 * <pre>
 *   regret_k(p) = Σ_{i=2..k} [ Δ_i(p) − Δ_1(p) ]
 * </pre>
 * <p>donde Δ_i(p) es el costo de insertar p en su i-ésima mejor unidad. En cada paso se
 * inserta el pedido de <b>mayor</b> arrepentimiento en su mejor posición. La lógica es directa:
 * un pedido con alternativas equivalentes puede esperar sin costo, mientras que uno que solo
 * encaja bien en un sitio debe colocarse antes de que ese sitio se ocupe.</p>
 *
 * <p>Cuando un pedido tiene menos de k alternativas su arrepentimiento se define como infinito,
 * de modo que se coloca de inmediato. Ese caso —el pedido que solo cabe en una unidad— es
 * justamente el de los pedidos priorizados de 4 horas cuando la flota está cargada, y es la
 * razón por la que este operador domina en las fases cercanas al colapso: prioriza los pedidos
 * de inserción más difícil, que son los que deciden si el escenario sobrevive.</p>
 */
public class InsercionPorArrepentimiento implements OperadorReparacion {

    private final int orden;
    private final double factorRuido;

    public InsercionPorArrepentimiento(int orden, double factorRuido) {
        if (orden < 2) {
            throw new IllegalArgumentException("El orden del arrepentimiento debe ser ≥ 2");
        }
        this.orden = orden;
        this.factorRuido = factorRuido;
    }

    @Override
    public void reparar(Solucion solucion, List<Pedido> porInsertar,
                        ContextoPlanificacion ctx, Random aleatorio) {
        CacheInserciones cache = new CacheInserciones(solucion, ctx, porInsertar, true);
        double amplitud = factorRuido
                * (pe.pucp.paqrap.modelo.Coordenada.ANCHO_MAX + pe.pucp.paqrap.modelo.Coordenada.ALTO_MAX);

        while (!cache.vacia()) {
            Pedido elegido = null;
            Insercion insercionElegida = null;
            double mejorArrepentimiento = Double.NEGATIVE_INFINITY;
            double desempateDelta = Double.POSITIVE_INFINITY;

            for (Pedido p : cache.pendientes()) {
                Insercion mejor = cache.mejor(p);
                if (mejor == null) {
                    cache.descartar(p);
                    solucion.marcarNoAsignado(p);
                    continue;
                }
                double regret = cache.arrepentimiento(p, orden);
                if (regret == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                if (Double.isFinite(regret)) {
                    regret += amplitud * (2 * aleatorio.nextDouble() - 1);
                }
                // Mayor arrepentimiento primero; a igualdad, la inserción más barata.
                if (regret > mejorArrepentimiento
                        || (regret == mejorArrepentimiento && mejor.delta < desempateDelta)) {
                    mejorArrepentimiento = regret;
                    desempateDelta = mejor.delta;
                    insercionElegida = mejor;
                    elegido = p;
                }
            }
            if (elegido == null) {
                break;
            }
            if (!cache.aplicar(elegido, insercionElegida)) {
                continue;
            }
        }
        for (Pedido p : cache.pendientes()) {
            solucion.marcarNoAsignado(p);
        }
    }

    @Override
    public String nombre() {
        return "insercion-arrepentimiento-" + orden;
    }
}
