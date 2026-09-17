package pe.pucp.paqrap.alns.reparacion;

import pe.pucp.paqrap.alns.CacheInserciones;
import pe.pucp.paqrap.servicios.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.alns.OperadorReparacion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.List;
import java.util.Random;

/**
 * Inserción golosa con ruido (<i>greedy insertion with noise</i>).
 *
 * <p>En cada paso coloca el pedido cuya mejor inserción sea la más barata de todas las
 * disponibles, y repite hasta que no queden pendientes colocables. Es una selección
 * <b>global</b>, no un recorrido en orden fijo: qué pedido se inserta primero se decide
 * comparando todas las alternativas, y eso es lo que hace a este reparador claramente superior
 * a insertar los pedidos uno tras otro.</p>
 *
 * <h2>Ruido</h2>
 * <p>La versión puramente golosa es determinista y tiende a reconstruir siempre la misma
 * solución tras destrucciones parecidas, lo que desperdicia iteraciones. Se perturba entonces
 * cada costo con ruido uniforme proporcional al tamaño del mapa:</p>
 * <pre>
 *   Δ' = Δ + η · d_max · tarifa · U(−1, 1)
 * </pre>
 * <p>El factor η controla la intensidad. Con η = 0 el operador es el goloso clásico.</p>
 *
 * <h2>Miopía deliberada</h2>
 * <p>El defecto conocido del goloso es que deja para el final los pedidos difíciles, que
 * terminan sin sitio. No se corrige aquí: es exactamente la debilidad que compensan los
 * operadores de arrepentimiento, y mantener ambos en la cartera es lo que permite al mecanismo
 * adaptativo elegir el más conveniente en cada fase de la búsqueda.</p>
 */
public class InsercionGolosa implements OperadorReparacion {

    private final double factorRuido;

    public InsercionGolosa(double factorRuido) {
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
            Insercion mejorInsercion = null;
            double mejorValor = Double.POSITIVE_INFINITY;

            for (Pedido p : cache.pendientes()) {
                Insercion ins = cache.mejor(p);
                if (ins == null) {
                    cache.descartar(p);            // sin ubicación posible en este ciclo
                    solucion.marcarNoAsignado(p);
                    continue;
                }
                double ruido = amplitud * (2 * aleatorio.nextDouble() - 1);
                double valor = ins.delta + ruido;
                if (valor < mejorValor) {
                    mejorValor = valor;
                    mejorInsercion = ins;
                    elegido = p;
                }
            }
            if (elegido == null) {
                break;
            }
            if (!cache.aplicar(elegido, mejorInsercion)) {
                continue;   // el inventario cambió: la fila se recalculó, se reintenta
            }
        }
        for (Pedido p : cache.pendientes()) {
            solucion.marcarNoAsignado(p);
        }
    }

    @Override
    public String nombre() {
        return factorRuido > 0 ? "insercion-golosa-ruido" : "insercion-golosa";
    }
}
