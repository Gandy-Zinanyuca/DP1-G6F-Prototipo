package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Mantenimiento;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Fotografía de la operación en el instante de planificación T.
 *
 * <p>Contiene todo lo que el algoritmo necesita: el instante simulado, los pedidos considerados,
 * las unidades disponibles, los mantenimientos y el inventario por almacén. Es el único objeto
 * que ALNS y Búsqueda Tabú reciben, lo que garantiza que ambos resuelven exactamente el mismo
 * problema en la experimentación numérica.</p>
 */
public class ContextoPlanificacion {

    private final Instancia instancia;
    private final int minutoActual;
    private final ParametrosPlanificador parametros;

    private final List<Pedido> pedidosPorAtender;
    private final List<Vehiculo> unidadesAsignables;
    private final List<Almacen> almacenes;

    /** Claves "códigoUnidad#díaSimulado" de los mantenimientos programados. */
    private final Set<String> mantenimientos = new HashSet<>();

    /** Cantidad por planificar de cada pedido considerado, por pedido original. */
    private final Map<Pedido, Integer> cantidadRequerida = new IdentityHashMap<>();

    public ContextoPlanificacion(Instancia instancia, int minutoActual,
                                 List<Pedido> pedidosPorAtender,
                                 List<Vehiculo> unidadesAsignables,
                                 ParametrosPlanificador parametros) {
        this.instancia = instancia;
        this.minutoActual = minutoActual;
        this.pedidosPorAtender = pedidosPorAtender;
        this.unidadesAsignables = unidadesAsignables;
        this.parametros = parametros;
        this.almacenes = instancia.getAlmacenes();
        for (Mantenimiento m : instancia.getMantenimientos()) {
            mantenimientos.add(m.getCodigoUnidad() + "#" + instancia.diaSimulado(m));
        }
        for (Pedido p : pedidosPorAtender) {
            cantidadRequerida.put(p.getOriginal(), p.getCantidad());
        }
        instancia.getMapa().fijarInstante(minutoActual);
    }

    /**
     * Construye el contexto de la ejecución en el instante T.
     *
     * <p>Pedidos considerados (ISA 5.1, ventana de consumo Sc = Sa × K): los pendientes —no
     * entregados y registrados hasta T— más los registrados en (T, T + Sc]. De cada pedido se
     * planifica solo la cantidad que aún no se despachó: si una parte ya salió en una unidad, se
     * considera una fracción con el resto. Unidades: las que no están averiadas ni en
     * mantenimiento preventivo en T.</p>
     *
     * @param scMinutos salto de consumo Sc = Sa × K, en minutos
     */
    public static ContextoPlanificacion construir(Instancia instancia, int minutoActual, int scMinutos,
                                                  List<Pedido> pedidosVivos,
                                                  List<String> unidadesNoDisponibles,
                                                  ParametrosPlanificador parametros) {
        List<Pedido> considerados = new ArrayList<>();
        int finVentana = minutoActual + scMinutos;
        for (Pedido p : pedidosVivos) {
            if (p.getMinutoRegistro() > finVentana) {
                continue;
            }
            if (p.getEstado() == Pedido.Estado.ENTREGADO
                    || p.getEstado() == Pedido.Estado.NO_CUMPLIDO) {
                continue;
            }
            int pendiente = p.cantidadPendiente();
            if (pendiente <= 0) {
                continue;   // ya despachado por completo: viaja en una unidad
            }
            considerados.add(pendiente == p.getCantidad() ? p : p.fraccion(pendiente));
        }

        List<Vehiculo> asignables = new ArrayList<>();
        List<String> enMantenimiento = instancia.unidadesEnMantenimiento(Turnos.dia(minutoActual));
        for (Vehiculo v : instancia.getFlota()) {
            if (!v.asignable()) {
                continue;
            }
            if (enMantenimiento.contains(v.getCodigo())
                    || unidadesNoDisponibles.contains(v.getCodigo())) {
                continue;
            }
            asignables.add(v);
        }
        return new ContextoPlanificacion(instancia, minutoActual, considerados, asignables, parametros);
    }

    public Instancia getInstancia() {
        return instancia;
    }

    public MapaUrbano getMapa() {
        return instancia.getMapa();
    }

    public int getMinutoActual() {
        return minutoActual;
    }

    public ParametrosPlanificador getParametros() {
        return parametros;
    }

    public List<Pedido> getPedidosPorAtender() {
        return pedidosPorAtender;
    }

    public List<Vehiculo> getUnidadesAsignables() {
        return unidadesAsignables;
    }

    public List<Almacen> getAlmacenes() {
        return almacenes;
    }

    /**
     * Cantidad que la solución debe cubrir del pedido (por su original); 0 si el pedido no
     * está entre los considerados.
     */
    public int cantidadRequerida(Pedido pedido) {
        return cantidadRequerida.getOrDefault(pedido.getOriginal(), 0);
    }

    /** Indica si la unidad tiene mantenimiento preventivo programado el día simulado indicado. */
    public boolean enMantenimiento(String codigoUnidad, int dia) {
        return mantenimientos.contains(codigoUnidad + "#" + dia);
    }

    /** Stock inicial del almacén al comenzar la ejecución, antes de las reservas de la solución. */
    public int stockInicial(Almacen almacen) {
        return almacen.esCentral() ? Integer.MAX_VALUE : almacen.getStock();
    }

    /** Almacén más cercano (distancia de retícula) al nodo indicado, para el regreso (LE020). */
    public Almacen almacenMasCercano(Coordenada desde) {
        Almacen mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Almacen a : almacenes) {
            int d = desde.distanciaManhattan(a.getUbicacion());
            if (d < mejorDistancia) {
                mejorDistancia = d;
                mejor = a;
            }
        }
        return mejor;
    }
}
