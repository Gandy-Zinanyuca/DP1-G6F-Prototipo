package pe.pucp.paqrap.solucion;

import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solución completa de un ciclo de planificación: una ruta por unidad utilizada más el
 * conjunto de pedidos que quedaron sin asignar.
 *
 * <h2>Estructura de datos</h2>
 * <pre>
 * Solucion
 *   ├── rutas          : LinkedHashMap&lt;codigoUnidad, Ruta&gt;   (orden estable ⇒ reproducible)
 *   ├── noAsignados    : LinkedHashSet&lt;Pedido&gt;
 *   ├── ubicacion      : HashMap&lt;idPedido, codigoUnidad&gt;      (índice inverso, O(1))
 *   └── consumoAlmacen : HashMap&lt;idAlmacen, unidades&gt;         (control de LE019)
 * </pre>
 *
 * <p>El índice inverso {@code ubicacion} es lo que hace baratos los operadores de destrucción:
 * remover un pedido no exige recorrer todas las rutas, sino localizar la suya en O(1). El
 * registro {@code consumoAlmacen} mantiene el acoplamiento de inventario entre rutas, que es
 * una restricción global y por eso no puede vivir dentro de una ruta individual.</p>
 *
 * <p>El uso de colecciones con orden de inserción estable (LinkedHashMap / LinkedHashSet) no
 * es cosmético: junto con una semilla fija del generador aleatorio, es lo que permite que dos
 * ejecuciones del mismo escenario produzcan resultados idénticos, como exigen LE008 y LE009.</p>
 */
public class Solucion {

    private final Map<String, Ruta> rutas = new LinkedHashMap<>();
    private final Set<Pedido> noAsignados = new LinkedHashSet<>();
    private final Map<Integer, String> ubicacion = new LinkedHashMap<>();
    private final Map<String, Integer> consumoAlmacen = new LinkedHashMap<>();

    private double costo = Double.NaN;
    private int tardanzaTotalMinutos;
    private int pedidosTardios;
    private double distanciaTotalKm;
    private double costoOperacionSoles;

    public Solucion() {
    }

    /** Copia profunda: cada ruta se clona, de modo que los operadores no comparten estado. */
    public Solucion copia() {
        Solucion s = new Solucion();
        for (Map.Entry<String, Ruta> e : rutas.entrySet()) {
            s.rutas.put(e.getKey(), e.getValue().copia());
        }
        s.noAsignados.addAll(noAsignados);
        s.ubicacion.putAll(ubicacion);
        s.consumoAlmacen.putAll(consumoAlmacen);
        s.costo = costo;
        s.tardanzaTotalMinutos = tardanzaTotalMinutos;
        s.pedidosTardios = pedidosTardios;
        s.distanciaTotalKm = distanciaTotalKm;
        s.costoOperacionSoles = costoOperacionSoles;
        return s;
    }

    // ------------------------------------------------------------------ estructura

    public Collection<Ruta> getRutas() {
        return rutas.values();
    }

    public Ruta getRuta(String codigoUnidad) {
        return rutas.get(codigoUnidad);
    }

    public Ruta getRuta(Vehiculo v) {
        return rutas.get(v.getCodigo());
    }

    /** Devuelve la ruta de la unidad, creándola vacía sobre el almacén indicado si no existía. */
    public Ruta rutaDe(Vehiculo v, Almacen almacenPorDefecto) {
        Ruta r = rutas.get(v.getCodigo());
        if (r == null) {
            r = new Ruta(v, almacenPorDefecto);
            rutas.put(v.getCodigo(), r);
        }
        return r;
    }

    public Set<Pedido> getNoAsignados() {
        return noAsignados;
    }

    public void marcarNoAsignado(Pedido p) {
        noAsignados.add(p);
        ubicacion.remove(p.getId());
    }

    /** Unidad que transporta el pedido, o {@code null} si está sin asignar. */
    public String unidadDe(Pedido p) {
        return ubicacion.get(p.getId());
    }

    /**
     * Inserta el pedido en la posición indicada de la ruta de la unidad, actualizando el índice
     * inverso y el consumo del almacén de origen. No verifica factibilidad: el llamador ya la
     * comprobó al evaluar el costo de inserción.
     */
    public void asignar(Ruta ruta, int posicion, Pedido pedido) {
        ruta.insertar(posicion, pedido);
        noAsignados.remove(pedido);
        ubicacion.put(pedido.getId(), ruta.getVehiculo().getCodigo());
        consumoAlmacen.merge(ruta.getAlmacenOrigen().getId(), pedido.getCantidad(), Integer::sum);
    }

    /**
     * Extrae el pedido de la ruta que lo contiene y lo deja sin asignar. Es la operación
     * elemental de todos los operadores de destrucción del ALNS.
     *
     * @return la ruta de la que fue removido, o {@code null} si el pedido no estaba asignado
     */
    public Ruta desasignar(Pedido pedido) {
        String codigo = ubicacion.remove(pedido.getId());
        if (codigo == null) {
            return null;
        }
        Ruta r = rutas.get(codigo);
        if (r != null && r.remover(pedido)) {
            consumoAlmacen.merge(r.getAlmacenOrigen().getId(), -pedido.getCantidad(), Integer::sum);
        }
        noAsignados.add(pedido);
        return r;
    }

    /** Pedidos actualmente asignados a alguna ruta. */
    public List<Pedido> pedidosAsignados() {
        List<Pedido> lista = new ArrayList<>();
        for (Ruta r : rutas.values()) {
            lista.addAll(r.getSecuencia());
        }
        return lista;
    }

    public int numeroUnidadesUsadas() {
        int n = 0;
        for (Ruta r : rutas.values()) {
            if (!r.estaVacia()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ inventario (LE019)

    public int consumo(Almacen almacen) {
        return consumoAlmacen.getOrDefault(almacen.getId(), 0);
    }

    /** Verifica que el almacén pueda soportar una carga adicional sin dejar stock negativo. */
    public boolean hayStock(ContextoPlanificacion ctx, Almacen almacen, int cantidad) {
        if (almacen.esCentral()) {
            return true;
        }
        return ctx.stockInicial(almacen) - consumo(almacen) >= cantidad;
    }

    /** Cambia el almacén de origen de una ruta reasignando su consumo de inventario. */
    public void cambiarAlmacenOrigen(Ruta ruta, Almacen nuevo) {
        int carga = 0;
        for (Pedido p : ruta.getSecuencia()) {
            carga += p.getCantidad();
        }
        consumoAlmacen.merge(ruta.getAlmacenOrigen().getId(), -carga, Integer::sum);
        ruta.setAlmacenOrigen(nuevo);
        consumoAlmacen.merge(nuevo.getId(), carga, Integer::sum);
    }

    // ------------------------------------------------------------------ evaluación

    /**
     * Recalcula todas las rutas y evalúa la función objetivo.
     *
     * <p>f(S) = w<sub>d</sub>·Σ costo(r) + w<sub>u</sub>·|unidades usadas|
     *          + w<sub>t</sub>·Σ tardanza(p) + Σ penalización(p ∉ S)</p>
     *
     * <p>Los pesos están escalados para inducir un orden lexicográfico de facto: eliminar
     * tardanzas domina sobre colocar todos los pedidos, y colocar pedidos domina sobre el costo
     * de operación. La razón es la definición de colapso del problema: una sola entrega fuera
     * de plazo termina el escenario, por lo que ninguna reducción de kilómetros compensa un
     * minuto de tardanza.</p>
     *
     * @return el valor de la función objetivo (a minimizar)
     */
    public double evaluar(ContextoPlanificacion ctx) {
        ParametrosPlanificador par = ctx.getParametros();

        distanciaTotalKm = 0;
        costoOperacionSoles = 0;
        tardanzaTotalMinutos = 0;
        pedidosTardios = 0;
        double f = 0;

        for (Ruta r : rutas.values()) {
            r.recalcular(ctx);
            if (r.estaVacia()) {
                continue;
            }
            distanciaTotalKm += r.getDistanciaKm();
            costoOperacionSoles += r.costoOperacion();
            tardanzaTotalMinutos += r.getTardanzaTotalMinutos();
            pedidosTardios += r.getPedidosTardios();

            f += par.costoFijoPorUnidad;
            if (!r.esFactible()) {
                // Una ruta estructuralmente infactible (capacidad, turno, nodo aislado) se
                // penaliza como si todos sus pedidos llegaran fuera de plazo.
                f += par.penalizacionPedidoTardio * r.tamanio();
            }
        }

        f += par.pesoCostoDistancia * costoOperacionSoles;
        f += par.penalizacionPedidoTardio * pedidosTardios;
        f += par.penalizacionPorMinutoTardanza * tardanzaTotalMinutos;

        for (Pedido p : noAsignados) {
            double criticidad = Math.min(1.5, Math.max(0.0, p.criticidad(ctx.getMinutoActual())));
            f += par.penalizacionPedidoNoAsignado + par.factorCriticidadNoAsignado * criticidad;
        }

        // Sobregiro de inventario: nunca debería ocurrir si los operadores respetan hayStock().
        for (Almacen a : ctx.getAlmacenes()) {
            if (a.esCentral()) {
                continue;
            }
            int exceso = consumo(a) - ctx.stockInicial(a);
            if (exceso > 0) {
                f += par.penalizacionPedidoTardio * exceso;
            }
        }

        costo = f;
        return f;
    }

    public double getCosto() {
        return costo;
    }

    public int getTardanzaTotalMinutos() {
        return tardanzaTotalMinutos;
    }

    public int getPedidosTardios() {
        return pedidosTardios;
    }

    public double getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public double getCostoOperacionSoles() {
        return costoOperacionSoles;
    }

    /** Una solución es admisible si coloca todos los pedidos sin ninguna tardanza. */
    public boolean esAdmisible() {
        if (!noAsignados.isEmpty() || pedidosTardios > 0) {
            return false;
        }
        for (Ruta r : rutas.values()) {
            if (!r.estaVacia() && !r.esFactible()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Solución  f=%.2f  unidades=%d  km=%.0f  costo=S/ %.2f  "
                        + "tardíos=%d  noAsignados=%d%n",
                costo, numeroUnidadesUsadas(), distanciaTotalKm, costoOperacionSoles,
                pedidosTardios, noAsignados.size()));
        for (Ruta r : rutas.values()) {
            if (!r.estaVacia()) {
                sb.append(r).append('\n');
            }
        }
        return sb.toString();
    }
}
