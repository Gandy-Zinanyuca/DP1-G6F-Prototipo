package pe.pucp.paqrap.solucion;

import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.TipoVehiculo;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;

import java.util.ArrayList;
import java.util.List;

/**
 * Ruta asignada a una unidad de transporte dentro de un ciclo de planificación.
 *
 * <h2>Representación</h2>
 * <p>Una ruta se representa como la terna <i>(unidad, almacén de origen, secuencia ordenada de
 * pedidos)</i>. Todo lo demás — tiempos de llegada, distancia, carga, hora de alimentación y
 * almacén de retorno — es <b>derivado</b> y se recalcula con {@link #recalcular} en una única
 * pasada hacia adelante de complejidad O(n) sobre la secuencia. Mantener la secuencia como
 * único dato primario es lo que permite que los operadores de destrucción y reparación del
 * ALNS trabajen con inserciones y remociones de posición sin invalidar el estado.</p>
 *
 * <h2>Restricciones absorbidas en la evaluación</h2>
 * <ul>
 *   <li>Capacidad de la unidad: 24 / 8 / 4 paquetes según el tipo (LE014, LE027).</li>
 *   <li>Hora límite de cada pedido: la tardanza se acumula en minutos (LE015).</li>
 *   <li>Tiempo de entrega de 1 hora por destinatario (LE016, LE023).</li>
 *   <li>Bloqueos vigentes: la distancia entre paradas la resuelve el mapa (LE075).</li>
 *   <li>Hora de alimentación de 1 hora, separada al menos 1 hora de los cambios de turno (LE018).</li>
 *   <li>Confinamiento de la ruta al turno en que inicia, si el parámetro lo exige (LE017).</li>
 *   <li>Retorno a un almacén al finalizar el recorrido (LE020).</li>
 * </ul>
 *
 * <p>La evaluación no rechaza rutas con tardanza: las contabiliza. Es la función objetivo la
 * que las penaliza con un factor lo bastante alto como para que la búsqueda las elimine antes
 * que cualquier otra consideración. Esto le da al ALNS la posibilidad de atravesar regiones
 * infactibles para llegar a mejores soluciones, que es justamente lo que un método de
 * vecindario pequeño no puede hacer.</p>
 */
public class Ruta {

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private final List<Pedido> secuencia = new ArrayList<>();

    // ---- Atributos derivados, recalculados por recalcular() ----
    private double distanciaKm;
    private int[] minutosLlegada = new int[0];
    private int minutoInicio;
    private int minutoRetorno;
    private int tardanzaTotalMinutos;
    private int pedidosTardios;
    private int cargaTotal;
    private boolean factible = true;
    private boolean alcanzable = true;
    private Almacen almacenRetorno;
    private int minutoInicioAlimentacion = -1;
    private String motivoInfactibilidad;

    public Ruta(Vehiculo vehiculo, Almacen almacenOrigen) {
        this.vehiculo = vehiculo;
        this.almacenOrigen = almacenOrigen;
    }

    /** Copia profunda de la ruta; los pedidos se comparten por referencia (son inmutables aquí). */
    public Ruta copia() {
        Ruta r = new Ruta(vehiculo, almacenOrigen);
        r.secuencia.addAll(secuencia);
        r.distanciaKm = distanciaKm;
        r.minutosLlegada = minutosLlegada.clone();
        r.minutoInicio = minutoInicio;
        r.minutoRetorno = minutoRetorno;
        r.tardanzaTotalMinutos = tardanzaTotalMinutos;
        r.pedidosTardios = pedidosTardios;
        r.cargaTotal = cargaTotal;
        r.factible = factible;
        r.alcanzable = alcanzable;
        r.almacenRetorno = almacenRetorno;
        r.minutoInicioAlimentacion = minutoInicioAlimentacion;
        r.motivoInfactibilidad = motivoInfactibilidad;
        return r;
    }

    // ------------------------------------------------------------------ estructura

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public Almacen getAlmacenOrigen() {
        return almacenOrigen;
    }

    public void setAlmacenOrigen(Almacen almacenOrigen) {
        this.almacenOrigen = almacenOrigen;
    }

    public List<Pedido> getSecuencia() {
        return secuencia;
    }

    public int tamanio() {
        return secuencia.size();
    }

    public boolean estaVacia() {
        return secuencia.isEmpty();
    }

    public void insertar(int posicion, Pedido pedido) {
        secuencia.add(posicion, pedido);
    }

    public Pedido remover(int posicion) {
        return secuencia.remove(posicion);
    }

    public boolean remover(Pedido pedido) {
        return secuencia.remove(pedido);
    }

    // ------------------------------------------------------------------ evaluación

    /**
     * Recalcula todos los atributos derivados de la ruta en una pasada hacia adelante.
     *
     * <p>Complejidad O(n) con n = número de paradas, dominada por las consultas de distancia
     * al mapa, que son O(1) sin bloqueos vigentes y O(1) amortizado con bloqueos gracias a la
     * memorización por nodo origen.</p>
     */
    public void recalcular(ContextoPlanificacion ctx) {
        ParametrosPlanificador par = ctx.getParametros();

        distanciaKm = 0;
        tardanzaTotalMinutos = 0;
        pedidosTardios = 0;
        cargaTotal = 0;
        factible = true;
        alcanzable = true;
        motivoInfactibilidad = null;
        minutoInicioAlimentacion = -1;
        minutosLlegada = new int[secuencia.size()];

        for (Pedido p : secuencia) {
            cargaTotal += p.getCantidad();
        }
        if (cargaTotal > vehiculo.getCapacidad()) {
            factible = false;
            motivoInfactibilidad = "capacidad excedida (" + cargaTotal + ">"
                    + vehiculo.getCapacidad() + ")";
        }

        minutoInicio = Math.max(vehiculo.getMinutoDisponibleDesde(), ctx.getMinutoActual());

        if (secuencia.isEmpty()) {
            minutoRetorno = minutoInicio;
            almacenRetorno = almacenOrigen;
            return;
        }

        // Ventana admisible de la hora de alimentación dentro del turno en que arranca la ruta.
        int inicioTurno = Turnos.inicioTurno(minutoInicio);
        int finTurno = inicioTurno + Turnos.DURACION_TURNO_MIN;
        int ventanaIni = inicioTurno + Turnos.SEPARACION_CAMBIO_TURNO_MIN;
        int ventanaFin = finTurno - Turnos.SEPARACION_CAMBIO_TURNO_MIN - Turnos.DURACION_ALMUERZO_MIN;
        boolean alimentacionPendiente = vehiculo.getTurnoDeUltimaAlimentacion() != inicioTurno;

        // Primera pasada sin alimentación: sirve para ubicar el mejor momento de tomarla.
        Recorrido sinAlimentacion = simular(ctx, Integer.MIN_VALUE, 0);
        if (!sinAlimentacion.alcanzable) {
            marcarInalcanzable(sinAlimentacion.motivo);
            return;
        }

        Recorrido definitivo = sinAlimentacion;
        if (alimentacionPendiente) {
            int posicion = ubicarAlimentacion(sinAlimentacion, ventanaIni, ventanaFin);
            if (posicion != Integer.MIN_VALUE) {
                int disponible = (posicion < 0)
                        ? sinAlimentacion.minutoEnOrigen
                        : sinAlimentacion.completado[posicion];
                int inicioComida = Math.max(disponible, ventanaIni);
                definitivo = simular(ctx, posicion, inicioComida);
                if (!definitivo.alcanzable) {
                    marcarInalcanzable(definitivo.motivo);
                    return;
                }
                minutoInicioAlimentacion = inicioComida;
            }
        }

        distanciaKm = definitivo.distancia;
        minutosLlegada = definitivo.llegada;
        tardanzaTotalMinutos = definitivo.tardanza;
        pedidosTardios = definitivo.tardios;
        almacenRetorno = definitivo.almacenRetorno;
        minutoRetorno = definitivo.minutoRetorno;

        if (par.limitarRutaAlTurno && minutoRetorno > finTurno) {
            factible = false;
            motivoInfactibilidad = "la ruta excede el turno (" + Turnos.formatear(minutoRetorno)
                    + " > " + Turnos.formatear(finTurno) + ")";
        }
        // La disponibilidad de inventario del almacén de origen (LE019) no se verifica aquí:
        // es una restricción que acopla varias rutas, y por eso la controla la solución
        // completa mediante su registro de consumo por almacén.
    }

    /**
     * Decide en qué punto de la ruta conviene ubicar la hora de alimentación.
     *
     * <p>La regla es <b>lo más tarde posible dentro de la ventana admisible</b>: se busca el
     * último punto del recorrido cuyo instante de disponibilidad no supere el cierre de la
     * ventana. Postergar la comida hasta el límite maximiza el número de entregas que se hacen
     * antes de la pausa y, por lo tanto, reduce la tardanza; tomarla en la primera oportunidad
     * —la regla ingenua— retrasa toda la ruta una hora sin ninguna contrapartida.</p>
     *
     * <p>Dos casos no consumen tiempo de ruta y devuelven "sin alimentación en ruta":</p>
     * <ul>
     *   <li>la ruta termina antes de que abra la ventana: la unidad come después;</li>
     *   <li>la ruta empieza después de que la ventana cierra: la unidad ya comió mientras
     *       estaba ociosa, antes de recibir esta asignación.</li>
     * </ul>
     *
     * @return índice de la entrega tras la cual se toma la comida, −1 para tomarla en el
     *         almacén antes de salir, o {@link Integer#MIN_VALUE} si no corresponde en ruta
     */
    private int ubicarAlimentacion(Recorrido base, int ventanaIni, int ventanaFin) {
        if (base.minutoEnOrigen > ventanaFin) {
            return Integer.MIN_VALUE;           // la jornada de reparto empieza pasada la ventana
        }
        if (base.minutoRetorno < ventanaIni) {
            return Integer.MIN_VALUE;           // la ruta termina antes de que abra la ventana
        }
        int elegida = -1;                       // por defecto, en el almacén antes de salir
        for (int i = 0; i < secuencia.size(); i++) {
            if (base.completado[i] <= ventanaFin) {
                elegida = i;
            } else {
                break;
            }
        }
        return elegida;
    }

    /** Resultado de una pasada de evaluación hacia adelante sobre la secuencia. */
    private static final class Recorrido {
        double distancia;
        int[] llegada;
        int[] completado;
        int minutoEnOrigen;
        int minutoRetorno;
        int tardanza;
        int tardios;
        Almacen almacenRetorno;
        boolean alcanzable = true;
        String motivo;
    }

    /**
     * Pasada de evaluación hacia adelante en O(n).
     *
     * @param posicionAlimentacion índice de la entrega tras la cual se inserta la hora de
     *                             alimentación, −1 para insertarla en el almacén de origen, o
     *                             {@link Integer#MIN_VALUE} para no insertarla
     * @param inicioAlimentacion   instante en que comienza la hora de alimentación
     */
    private Recorrido simular(ContextoPlanificacion ctx, int posicionAlimentacion,
                              int inicioAlimentacion) {
        ParametrosPlanificador par = ctx.getParametros();
        MapaUrbano mapa = ctx.getMapa();
        TipoVehiculo tipo = vehiculo.getTipo();

        Recorrido r = new Recorrido();
        r.llegada = new int[secuencia.size()];
        r.completado = new int[secuencia.size()];

        int t = minutoInicio;

        // Tramo inicial: de la posición actual de la unidad al almacén de origen.
        Coordenada pos = vehiculo.getPosicion();
        int dOrigen = mapa.distancia(pos, almacenOrigen.getUbicacion());
        if (dOrigen == Integer.MAX_VALUE) {
            r.alcanzable = false;
            r.motivo = "almacén de origen inalcanzable por bloqueos";
            return r;
        }
        r.distancia += dOrigen;
        t += tipo.minutosPara(dOrigen);
        pos = almacenOrigen.getUbicacion();
        r.minutoEnOrigen = t;

        if (posicionAlimentacion == -1) {
            t = Math.max(t, inicioAlimentacion) + Turnos.DURACION_ALMUERZO_MIN;
        }

        for (int i = 0; i < secuencia.size(); i++) {
            Pedido p = secuencia.get(i);
            int d = mapa.distancia(pos, p.getDestino());
            if (d == Integer.MAX_VALUE) {
                r.alcanzable = false;
                r.motivo = "destino " + p.getDestino() + " aislado por bloqueos";
                return r;
            }
            r.distancia += d;
            t += tipo.minutosPara(d);
            r.llegada[i] = t;

            if (t > p.getMinutoLimite()) {
                r.tardanza += t - p.getMinutoLimite();
                r.tardios++;
            }
            t += par.minutosEntrega;
            r.completado[i] = t;
            pos = p.getDestino();

            if (posicionAlimentacion == i) {
                t = Math.max(t, inicioAlimentacion) + Turnos.DURACION_ALMUERZO_MIN;
            }
        }

        // Retorno al almacén más cercano para la recarga (LE020).
        r.almacenRetorno = ctx.almacenMasCercano(pos);
        if (r.almacenRetorno == null) {
            r.almacenRetorno = almacenOrigen;
        }
        int dRetorno = mapa.distancia(pos, r.almacenRetorno.getUbicacion());
        if (dRetorno == Integer.MAX_VALUE) {
            r.alcanzable = false;
            r.motivo = "retorno a almacén inalcanzable por bloqueos";
            return r;
        }
        r.distancia += dRetorno;
        t += tipo.minutosPara(dRetorno);
        r.minutoRetorno = t;
        return r;
    }

    private void marcarInalcanzable(String motivo) {
        alcanzable = false;
        factible = false;
        motivoInfactibilidad = motivo;
        minutoRetorno = Integer.MAX_VALUE;
    }

    /** Costo monetario de operación de la ruta: distancia recorrida por la tarifa del tipo. */
    public double costoOperacion() {
        return distanciaKm * vehiculo.getTipo().getCostoPorKm();
    }

    // ------------------------------------------------------------------ derivados

    public double getDistanciaKm() {
        return distanciaKm;
    }

    public int minutoLlegada(int posicion) {
        return minutosLlegada[posicion];
    }

    public int[] getMinutosLlegada() {
        return minutosLlegada;
    }

    public int getMinutoInicio() {
        return minutoInicio;
    }

    public int getMinutoRetorno() {
        return minutoRetorno;
    }

    public int getTardanzaTotalMinutos() {
        return tardanzaTotalMinutos;
    }

    /** Número de pedidos de la ruta que llegarían después de su hora límite. */
    public int getPedidosTardios() {
        return pedidosTardios;
    }

    public int getCargaTotal() {
        return cargaTotal;
    }

    public boolean esFactible() {
        return factible && alcanzable;
    }

    public boolean esAlcanzable() {
        return alcanzable;
    }

    public Almacen getAlmacenRetorno() {
        return almacenRetorno;
    }

    public int getMinutoInicioAlimentacion() {
        return minutoInicioAlimentacion;
    }

    public String getMotivoInfactibilidad() {
        return motivoInfactibilidad;
    }

    /** Utilización de la capacidad de la unidad, para el semáforo de carga del visualizador. */
    public double utilizacion() {
        return cargaTotal / (double) vehiculo.getCapacidad();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(vehiculo.getCodigo()).append(" desde ").append(almacenOrigen.getId())
                .append(" carga=").append(cargaTotal).append('/').append(vehiculo.getCapacidad())
                .append(" km=").append(String.format("%.0f", distanciaKm))
                .append(" inicio=").append(Turnos.formatear(minutoInicio));
        for (int i = 0; i < secuencia.size(); i++) {
            sb.append("\n    -> ").append(secuencia.get(i))
                    .append(" llega ").append(Turnos.formatear(minutosLlegada[i]));
            int holgura = secuencia.get(i).getMinutoLimite() - minutosLlegada[i];
            sb.append(holgura >= 0 ? "  holgura " + holgura + " min"
                    : "  TARDE " + (-holgura) + " min");
        }
        if (minutoInicioAlimentacion >= 0) {
            sb.append("\n    (alimentación ").append(Turnos.formatear(minutoInicioAlimentacion)).append(')');
        }
        sb.append("\n    retorno ").append(almacenRetorno == null ? "?" : almacenRetorno.getId())
                .append(' ').append(Turnos.formatear(minutoRetorno));
        if (!esFactible()) {
            sb.append("  [INFACTIBLE: ").append(motivoInfactibilidad).append(']');
        }
        return sb.toString();
    }
}
