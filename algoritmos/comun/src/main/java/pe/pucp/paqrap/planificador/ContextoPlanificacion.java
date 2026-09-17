package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;

import java.util.ArrayList;
import java.util.List;

/**
 * Fotografía de la operación al inicio de un ciclo de planificación.
 *
 * <p>Contiene todo lo que el algoritmo necesita y nada más: el instante simulado, los pedidos
 * que deben colocarse, las unidades que admiten asignación y el inventario disponible por
 * almacén. Es el único objeto que ALNS y Búsqueda Tabú reciben, lo que garantiza que ambos
 * resuelven exactamente el mismo problema en la experimentación numérica.</p>
 *
 * <p>El contexto es de solo lectura para el algoritmo salvo por el inventario tentativo:
 * durante la construcción de rutas, cada asignación reserva unidades del almacén de origen y
 * cada remoción las devuelve, de modo que en todo momento se cumple LE019.</p>
 */
public class ContextoPlanificacion {

    private final Instancia instancia;
    private final int minutoActual;
    private final ParametrosPlanificador parametros;

    private final List<Pedido> pedidosPorAtender;
    private final List<Vehiculo> unidadesAsignables;
    private final List<Almacen> almacenes;

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
        instancia.getMapa().fijarInstante(minutoActual);
    }

    /**
     * Construye el contexto del ciclo a partir del estado global de la simulación: filtra los
     * pedidos ya registrados y aún no entregados (LE006) y las unidades que no están averiadas
     * ni en mantenimiento preventivo (LE087, LE089).
     */
    public static ContextoPlanificacion construir(Instancia instancia, int minutoActual,
                                                  List<Pedido> pedidosVivos,
                                                  List<String> unidadesNoDisponibles,
                                                  ParametrosPlanificador parametros) {
        List<Pedido> porAtender = new ArrayList<>();
        int horizonte = minutoActual + parametros.horizonteAtencionMinutos;
        for (Pedido p : pedidosVivos) {
            if (p.getMinutoRegistro() > minutoActual) {
                continue;                       // aún no ha sido registrado por el cliente
            }
            if (p.getEstado() == Pedido.Estado.ENTREGADO
                    || p.getEstado() == Pedido.Estado.NO_CUMPLIDO) {
                continue;
            }
            if (p.getMinutoLimite() > horizonte) {
                continue;                       // puede esperar a un ciclo posterior
            }
            porAtender.add(p);
        }

        // Prioriza por criticidad y acota el tamaño del problema del ciclo (LE096, LE097).
        porAtender.sort(java.util.Comparator
                .comparingInt((Pedido p) -> p.holgura(minutoActual))
                .thenComparingInt(Pedido::getId));
        if (porAtender.size() > parametros.maxPedidosPorCiclo) {
            porAtender = new ArrayList<>(porAtender.subList(0, parametros.maxPedidosPorCiclo));
        }

        List<Vehiculo> asignables = new ArrayList<>();
        int dia = Turnos.dia(minutoActual);
        List<String> enMantenimiento = instancia.unidadesEnMantenimiento(dia);
        for (Vehiculo v : instancia.getFlota()) {
            if (!v.asignable()) {
                continue;
            }
            if (enMantenimiento.contains(v.getCodigo())
                    || unidadesNoDisponibles.contains(v.getCodigo())) {
                continue;
            }
            if (v.getMinutoDisponibleDesde()
                    > minutoActual + parametros.ventanaDisponibilidadUnidadMinutos) {
                continue;   // sigue en ruta; no es capacidad real de este ciclo
            }
            asignables.add(v);
        }
        return new ContextoPlanificacion(instancia, minutoActual, porAtender, asignables, parametros);
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

    /** Stock inicial del almacén al comenzar el ciclo, antes de las reservas de la solución. */
    public int stockInicial(Almacen almacen) {
        return almacen.esCentral() ? Integer.MAX_VALUE : almacen.getStock();
    }

    /** Almacén más cercano al nodo indicado, usado para el retorno de las rutas (LE020). */
    public Almacen almacenMasCercano(Coordenada desde) {
        Almacen mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Almacen a : almacenes) {
            int d = getMapa().distancia(desde, a.getUbicacion());
            if (d < mejorDistancia) {
                mejorDistancia = d;
                mejor = a;
            }
        }
        return mejor;
    }

    /** Almacenes ordenados por cercanía al nodo indicado; el primero es el más próximo. */
    public List<Almacen> almacenesPorCercania(Coordenada desde) {
        List<Almacen> orden = new ArrayList<>(almacenes);
        final MapaUrbano mapa = getMapa();
        orden.sort((a, b) -> Integer.compare(
                mapa.distancia(desde, a.getUbicacion()),
                mapa.distancia(desde, b.getUbicacion())));
        return orden;
    }
}
