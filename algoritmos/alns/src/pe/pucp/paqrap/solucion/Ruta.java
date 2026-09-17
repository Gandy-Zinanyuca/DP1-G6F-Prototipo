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
import java.util.Set;

/**
 * Ruta asignada a una unidad de transporte dentro de una ejecución del planificador.
 *
 * <h2>Representación</h2>
 * <p>Una ruta se representa como la terna <i>(unidad, almacén de origen, secuencia ordenada de
 * pedidos)</i>. Todo lo demás —tiempos de llegada, distancia, carga, hora de alimentación y
 * almacén de retorno— es <b>derivado</b> y se recalcula con {@link #recalcular}.</p>
 *
 * <h2>Evaluación de la ruta (EVALUAR, ISA 5.1)</h2>
 * <p>Todas las restricciones son duras; cualquier violación deja la ruta no factible:</p>
 * <ul>
 *   <li>Capacidad de la unidad (LE014, LE027).</li>
 *   <li>Momento de salida = máx(T, disponibilidad de la unidad, registro más tardío de los
 *       pedidos cargados).</li>
 *   <li>Cada tramo se recorre con CAMINO_MÁS_RÁPIDO, respetando los bloqueos en la hora real
 *       de cruce (LE075).</li>
 *   <li>Llegada a cada destino no posterior a su hora límite (LE015, LE021).</li>
 *   <li>Tiempo de entrega de 1 hora por destinatario (LE016, LE023).</li>
 *   <li>Hora de alimentación de 1 hora, separada al menos 1 hora de los cambios de turno (LE018).</li>
 *   <li>Confinamiento al turno, si el parámetro lo exige (LE017).</li>
 *   <li>Sin mantenimiento preventivo en ningún día que abarque la ruta hasta el regreso.</li>
 *   <li>Regreso al almacén más cercano al finalizar (LE020).</li>
 * </ul>
 */
public class Ruta {

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private final List<Pedido> secuencia = new ArrayList<>();

    // ---- Atributos derivados, recalculados por recalcular() ----
    private boolean calculada = false;
    private double distanciaKm;
    private int[] minutosLlegada = new int[0];
    /** Salida (minuto con fracción) del tramo que llega al pedido i. */
    private double[] salidasTramo = new double[0];
    private int minutoInicio;
    private int minutoRetorno;
    private int cargaTotal;
    private boolean factible = true;
    private Almacen almacenRetorno;
    private int minutoInicioAlimentacion = -1;
    private String motivoInfactibilidad;

    public Ruta(Vehiculo vehiculo, Almacen almacenOrigen) {
        this.vehiculo = vehiculo;
        this.almacenOrigen = almacenOrigen;
    }

    /** Copia profunda de la ruta; los pedidos se comparten por referencia. */
    public Ruta copia() {
        Ruta r = new Ruta(vehiculo, almacenOrigen);
        r.secuencia.addAll(secuencia);
        r.calculada = calculada;
        r.distanciaKm = distanciaKm;
        r.minutosLlegada = minutosLlegada.clone();
        r.salidasTramo = salidasTramo.clone();
        r.minutoInicio = minutoInicio;
        r.minutoRetorno = minutoRetorno;
        r.cargaTotal = cargaTotal;
        r.factible = factible;
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
        calculada = false;
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
        calculada = false;
    }

    public Pedido remover(int posicion) {
        calculada = false;
        return secuencia.remove(posicion);
    }

    public boolean remover(Pedido pedido) {
        calculada = false;
        return secuencia.remove(pedido);
    }

    // ------------------------------------------------------------------ evaluación

    /** Recalcula la ruta solo si su estructura cambió desde el último cálculo. */
    public void asegurarCalculada(ContextoPlanificacion ctx) {
        if (!calculada) {
            recalcular(ctx);
        }
    }

    /** Recalcula todos los atributos derivados de la ruta. */
    public void recalcular(ContextoPlanificacion ctx) {
        ParametrosPlanificador par = ctx.getParametros();
        calculada = true;

        distanciaKm = 0;
        cargaTotal = 0;
        factible = true;
        motivoInfactibilidad = null;
        minutoInicioAlimentacion = -1;
        minutosLlegada = new int[secuencia.size()];
        salidasTramo = new double[secuencia.size()];

        int registroMasTardio = Integer.MIN_VALUE;
        for (Pedido p : secuencia) {
            cargaTotal += p.getCantidad();
            registroMasTardio = Math.max(registroMasTardio, p.getMinutoRegistro());
        }
        if (cargaTotal > vehiculo.getCapacidad()) {
            infactible("capacidad excedida (" + cargaTotal + ">" + vehiculo.getCapacidad() + ")");
        }

        minutoInicio = Math.max(Math.max(vehiculo.getMinutoDisponibleDesde(), ctx.getMinutoActual()),
                registroMasTardio);

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
            infactible(sinAlimentacion.motivo);
            minutoRetorno = Integer.MAX_VALUE;
            return;
        }

        Recorrido definitivo = sinAlimentacion;
        if (alimentacionPendiente) {
            int posicion = ubicarAlimentacion(sinAlimentacion, ventanaIni, ventanaFin);
            if (posicion != Integer.MIN_VALUE) {
                double disponible = (posicion < 0)
                        ? sinAlimentacion.minutoEnOrigen
                        : sinAlimentacion.completado[posicion];
                int inicioComida = (int) Math.ceil(Math.max(disponible, ventanaIni));
                definitivo = simular(ctx, posicion, inicioComida);
                if (!definitivo.alcanzable) {
                    infactible(definitivo.motivo);
                    minutoRetorno = Integer.MAX_VALUE;
                    return;
                }
                minutoInicioAlimentacion = inicioComida;
            }
        }

        distanciaKm = definitivo.distancia;
        minutosLlegada = definitivo.llegada;
        salidasTramo = definitivo.salida;
        almacenRetorno = definitivo.almacenRetorno;
        minutoRetorno = definitivo.minutoRetorno;

        // Deadline: restricción dura.
        for (int i = 0; i < secuencia.size(); i++) {
            Pedido p = secuencia.get(i);
            if (minutosLlegada[i] > p.getMinutoLimite()) {
                infactible("entrega fuera de plazo de P" + p.getId() + " ("
                        + Turnos.formatear(minutosLlegada[i]) + " > "
                        + Turnos.formatear(p.getMinutoLimite()) + ")");
                break;
            }
        }

        if (par.limitarRutaAlTurno && minutoRetorno > finTurno) {
            infactible("la ruta excede el turno (" + Turnos.formatear(minutoRetorno)
                    + " > " + Turnos.formatear(finTurno) + ")");
        }

        // Mantenimiento durante toda la ruta, hasta finalizar el regreso.
        for (int dia = Turnos.dia(minutoInicio); dia <= Turnos.dia(minutoRetorno); dia++) {
            if (ctx.enMantenimiento(vehiculo.getCodigo(), dia)) {
                infactible("mantenimiento de " + vehiculo.getCodigo() + " el día " + dia);
                break;
            }
        }
        // El inventario del almacén de origen (LE019) acopla varias rutas: lo valida la solución.
    }

    private void infactible(String motivo) {
        if (factible) {
            motivoInfactibilidad = motivo;
        }
        factible = false;
    }

    /**
     * Decide en qué punto de la ruta se ubica la hora de alimentación: lo más tarde posible
     * dentro de la ventana admisible.
     *
     * @return índice de la entrega tras la cual se toma la comida, −1 para tomarla en el
     *         almacén antes de salir, o {@link Integer#MIN_VALUE} si no corresponde en ruta
     */
    private int ubicarAlimentacion(Recorrido base, int ventanaIni, int ventanaFin) {
        if (base.minutoEnOrigen > ventanaFin) {
            return Integer.MIN_VALUE;
        }
        if (base.minutoRetorno < ventanaIni) {
            return Integer.MIN_VALUE;
        }
        int elegida = -1;
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
        double[] salida;
        double[] completado;
        double minutoEnOrigen;
        int minutoRetorno;
        Almacen almacenRetorno;
        boolean alcanzable = true;
        String motivo;
    }

    /**
     * Pasada de evaluación hacia adelante con CAMINO_MÁS_RÁPIDO en cada tramo.
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
        double velocidad = tipo.getVelocidadKmH();

        Recorrido r = new Recorrido();
        r.llegada = new int[secuencia.size()];
        r.salida = new double[secuencia.size()];
        r.completado = new double[secuencia.size()];

        double t = minutoInicio;

        // Tramo inicial: de la posición actual de la unidad al almacén de origen.
        Coordenada pos = vehiculo.getPosicion();
        MapaUrbano.Tramo tramo = mapa.caminoMasRapido(pos, almacenOrigen.getUbicacion(), t, velocidad, false);
        if (tramo == null) {
            r.alcanzable = false;
            r.motivo = "almacén de origen inalcanzable";
            return r;
        }
        r.distancia += tramo.km;
        t = tramo.llegada;
        pos = almacenOrigen.getUbicacion();
        r.minutoEnOrigen = t;

        if (posicionAlimentacion == -1) {
            t = Math.max(t, inicioAlimentacion) + Turnos.DURACION_ALMUERZO_MIN;
        }

        for (int i = 0; i < secuencia.size(); i++) {
            Pedido p = secuencia.get(i);
            r.salida[i] = t;
            tramo = mapa.caminoMasRapido(pos, p.getDestino(), t, velocidad, false);
            if (tramo == null) {
                r.alcanzable = false;
                r.motivo = "desplazamiento imposible hacia " + p.getDestino();
                return r;
            }
            r.distancia += tramo.km;
            t = tramo.llegada;
            r.llegada[i] = (int) Math.ceil(t - 1e-9);

            t += par.minutosEntrega;
            r.completado[i] = t;
            pos = p.getDestino();

            if (posicionAlimentacion == i) {
                t = Math.max(t, inicioAlimentacion) + Turnos.DURACION_ALMUERZO_MIN;
            }
        }

        // Regreso al almacén más cercano para la recarga (LE020).
        r.almacenRetorno = ctx.almacenMasCercano(pos);
        if (r.almacenRetorno == null) {
            r.almacenRetorno = almacenOrigen;
        }
        tramo = mapa.caminoMasRapido(pos, r.almacenRetorno.getUbicacion(), t, velocidad, false);
        if (tramo == null) {
            r.alcanzable = false;
            r.motivo = "regreso al almacén imposible";
            return r;
        }
        r.distancia += tramo.km;
        r.minutoRetorno = (int) Math.ceil(tramo.llegada - 1e-9);
        return r;
    }

    /**
     * Pedidos de la ruta cuyo tramo de llegada, según el camino calculado, atraviesa alguna de
     * las calles indicadas. Lo usa el operador <i>blocked-arc removal</i>.
     */
    public List<Pedido> pedidosQueAtraviesan(Set<Long> arcos, ContextoPlanificacion ctx) {
        List<Pedido> afectados = new ArrayList<>();
        asegurarCalculada(ctx);
        if (arcos.isEmpty() || secuencia.isEmpty() || salidasTramo.length != secuencia.size()) {
            return afectados;
        }
        MapaUrbano mapa = ctx.getMapa();
        double velocidad = vehiculo.getTipo().getVelocidadKmH();
        Coordenada anterior = almacenOrigen.getUbicacion();
        for (int i = 0; i < secuencia.size(); i++) {
            Pedido p = secuencia.get(i);
            MapaUrbano.Tramo tramo = mapa.caminoMasRapido(anterior, p.getDestino(), salidasTramo[i],
                    velocidad, true);
            if (tramo != null) {
                for (Long arco : tramo.arcos) {
                    if (arcos.contains(arco)) {
                        afectados.add(p);
                        break;
                    }
                }
            }
            anterior = p.getDestino();
        }
        return afectados;
    }

    /** Costo de la ruta: distancia recorrida × costo por km del tipo de unidad. */
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

    public int getCargaTotal() {
        int carga = 0;
        for (Pedido p : secuencia) {
            carga += p.getCantidad();
        }
        return carga;
    }

    public boolean esFactible() {
        return factible;
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
        return getCargaTotal() / (double) vehiculo.getCapacidad();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(vehiculo.getCodigo()).append(" desde ").append(almacenOrigen.getId())
                .append(" carga=").append(cargaTotal).append('/').append(vehiculo.getCapacidad())
                .append(" km=").append(String.format("%.0f", distanciaKm))
                .append(" inicio=").append(Turnos.formatear(minutoInicio));
        for (int i = 0; i < secuencia.size(); i++) {
            sb.append("\n    -> ").append(secuencia.get(i));
            if (i < minutosLlegada.length) {
                sb.append(" llega ").append(Turnos.formatear(minutosLlegada[i]));
                int holgura = secuencia.get(i).getMinutoLimite() - minutosLlegada[i];
                sb.append(holgura >= 0 ? "  holgura " + holgura + " min"
                        : "  TARDE " + (-holgura) + " min");
            }
        }
        if (minutoInicioAlimentacion >= 0) {
            sb.append("\n    (alimentación ").append(Turnos.formatear(minutoInicioAlimentacion)).append(')');
        }
        sb.append("\n    retorno ").append(almacenRetorno == null ? "?" : almacenRetorno.getId())
                .append(' ').append(Turnos.formatear(minutoRetorno));
        if (!esFactible()) {
            sb.append("  [NO FACTIBLE: ").append(motivoInfactibilidad).append(']');
        }
        return sb.toString();
    }
}
