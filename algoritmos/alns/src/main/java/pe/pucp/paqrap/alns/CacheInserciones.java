package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.servicios.EvaluadorInsercion;

import pe.pucp.paqrap.servicios.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caché incremental de costos de inserción, compartida por los operadores de reparación.
 *
 * <h2>Por qué existe</h2>
 * <p>Un reparador goloso "global" —el que en cada paso elige el par (pedido, posición) de menor
 * costo entre todos los pendientes— es notablemente mejor que insertar los pedidos en un orden
 * fijo, pero recalcular todo en cada paso cuesta O(q·V·n²) por paso y O(q²·V·n²) por
 * reparación, lo que vuelve inviables las miles de iteraciones que el ALNS necesita.</p>
 *
 * <p>La observación que lo arregla es que el costo de inserción de un pedido en una ruta
 * <b>solo depende de esa ruta</b>. Cuando se inserta un pedido en la unidad U, únicamente las
 * entradas correspondientes a U quedan obsoletas; las de las demás unidades siguen siendo
 * válidas. La caché aprovecha esto:</p>
 * <ul>
 *   <li>construcción inicial: O(q·V·n²), una sola vez por reparación;</li>
 *   <li>tras cada inserción: O(q·n²) para refrescar una sola columna.</li>
 * </ul>
 *
 * <h2>Estructura</h2>
 * <pre>
 *   tabla : LinkedHashMap&lt;Pedido, LinkedHashMap&lt;codigoUnidad, Insercion&gt;&gt;
 * </pre>
 * <p>El orden de inserción estable de ambos niveles garantiza que los empates se resuelvan
 * siempre igual, condición necesaria para la reproducibilidad exigida por LE008.</p>
 */
public class CacheInserciones {

    private final Solucion solucion;
    private final ContextoPlanificacion ctx;
    private final boolean soloAdmisibles;
    private final Map<Pedido, Map<String, Insercion>> tabla = new LinkedHashMap<>();

    public CacheInserciones(Solucion solucion, ContextoPlanificacion ctx,
                            List<Pedido> pendientes, boolean soloAdmisibles) {
        this.solucion = solucion;
        this.ctx = ctx;
        this.soloAdmisibles = soloAdmisibles;
        for (Pedido p : pendientes) {
            tabla.put(p, calcularFila(p));
        }
    }

    private Map<String, Insercion> calcularFila(Pedido p) {
        Map<String, Insercion> fila = new LinkedHashMap<>();
        for (Insercion ins : EvaluadorInsercion.porUnidad(solucion, p, ctx, soloAdmisibles)) {
            fila.put(ins.ruta.getVehiculo().getCodigo(), ins);
        }
        return fila;
    }

    public List<Pedido> pendientes() {
        return new ArrayList<>(tabla.keySet());
    }

    public boolean vacia() {
        return tabla.isEmpty();
    }

    /** Mejor inserción registrada para el pedido, o {@code null} si no hay ninguna posible. */
    public Insercion mejor(Pedido p) {
        Insercion mejor = null;
        Map<String, Insercion> fila = tabla.get(p);
        if (fila == null) {
            return null;
        }
        for (Insercion ins : fila.values()) {
            if (mejor == null || ins.delta < mejor.delta) {
                mejor = ins;
            }
        }
        return mejor;
    }

    /** Todas las alternativas registradas para el pedido, una por unidad, sin ordenar. */
    public List<Insercion> alternativas(Pedido p) {
        Map<String, Insercion> fila = tabla.get(p);
        return fila == null ? new ArrayList<>() : new ArrayList<>(fila.values());
    }

    /**
     * Arrepentimiento de orden k: diferencia entre el costo de la k-ésima mejor unidad y el de
     * la mejor. Mide cuánto se pierde por no colocar el pedido ahora en su mejor sitio. Si el
     * pedido tiene menos de k alternativas, el arrepentimiento es infinito: es el caso de los
     * pedidos que solo caben en una unidad y por eso deben colocarse antes que ningún otro.
     */
    public double arrepentimiento(Pedido p, int k) {
        Map<String, Insercion> fila = tabla.get(p);
        if (fila == null || fila.isEmpty()) {
            return Double.NEGATIVE_INFINITY;   // no hay dónde ponerlo: no compite
        }
        List<Insercion> ordenadas = new ArrayList<>(fila.values());
        ordenadas.sort((a, b) -> Double.compare(a.delta, b.delta));
        if (ordenadas.size() < k) {
            return Double.POSITIVE_INFINITY;
        }
        double suma = 0;
        for (int i = 1; i < k; i++) {
            suma += ordenadas.get(i).delta - ordenadas.get(0).delta;
        }
        return suma;
    }

    /**
     * Aplica la inserción del pedido y refresca únicamente la columna de la unidad afectada.
     *
     * @return {@code false} si la inserción dejó de ser válida por falta de inventario, en cuyo
     *         caso la fila del pedido se recalcula y el llamador debe reintentar
     */
    public boolean aplicar(Pedido p, Insercion ins) {
        if (!solucion.hayStock(ctx, ins.almacenOrigen, p.getCantidad())) {
            tabla.put(p, calcularFila(p));
            return false;
        }
        EvaluadorInsercion.aplicar(solucion, ins, p, ctx);
        tabla.remove(p);

        String unidadAfectada = ins.ruta.getVehiculo().getCodigo();
        Vehiculo v = ins.ruta.getVehiculo();
        for (Map.Entry<Pedido, Map<String, Insercion>> fila : tabla.entrySet()) {
            Insercion nueva = EvaluadorInsercion.mejorEnUnidad(
                    solucion, fila.getKey(), v, ctx, soloAdmisibles);
            if (nueva == null) {
                fila.getValue().remove(unidadAfectada);
            } else {
                fila.getValue().put(unidadAfectada, nueva);
            }
        }
        return true;
    }

    /** Retira el pedido de la caché sin insertarlo: no existe ubicación posible en este ciclo. */
    public void descartar(Pedido p) {
        tabla.remove(p);
    }
}
