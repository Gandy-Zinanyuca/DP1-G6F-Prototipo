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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ruta asignada a una unidad de transporte dentro de una ejecución del planificador.
 *
 * <h2>Representación</h2>
 * <p>Una ruta se representa como la terna <i>(unidad, almacén de origen, secuencia ordenada de
 * pedidos)</i>. Todo lo demás —viajes, tiempos de llegada, distancia, carga, hora de
 * alimentación, consumo de inventario y almacén de retorno— es <b>derivado</b> y se recalcula
 * con {@link #recalcular}.</p>
 *
 * <h2>Viajes y recargas</h2>
 * <p>La unidad puede recargar en cualquier almacén con stock y seguir repartiendo (enunciado del
 * curso). La secuencia se reparte en <i>viajes</i> de forma determinista: la unidad carga en el
 * almacén de origen y entrega en orden; cuando el siguiente pedido ya no cabe en la carga del
 * viaje, va al almacén más cercano con stock suficiente, recarga y continúa. Así el orden de la
 * secuencia —que deciden la inserción y los operadores de ALNS— determina también dónde recargar:
 * seguir la ruta o volver al almacén es parte de la misma decisión. Con
 * {@link ParametrosPlanificador#permitirRecargas} desactivado la ruta es un único viaje.</p>
 *
 * <h2>Evaluación de la ruta (EVALUAR, ISA 5.1)</h2>
 * <p>Todas las restricciones son duras; cualquier violación deja la ruta no factible:</p>
 * <ul>
 *   <li>Capacidad de la unidad en cada viaje (LE014, LE027) y a lo sumo
 *       {@link ParametrosPlanificador#maxViajesPorRuta} viajes.</li>
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
 * <p>El inventario de los almacenes intermedios acopla varias rutas: lo valida la solución.</p>
 */
public class Ruta {

    /**
     * Viaje de la ruta: carga en un almacén, entrega los pedidos [desde, hasta) de la secuencia y
     * termina en un almacén (el de la siguiente recarga o el de retorno).
     */
    public static final class Viaje {
        private final int desde;
        private final int hasta;
        private final int salida;
        private final int fin;
        private final Almacen almacenCarga;
        private final Almacen almacenFin;
        private final int carga;
        private final double distanciaKm;

        Viaje(int desde, int hasta, int salida, int fin, Almacen almacenCarga, Almacen almacenFin,
              int carga, double distanciaKm) {
            this.desde = desde;
            this.hasta = hasta;
            this.salida = salida;
            this.fin = fin;
            this.almacenCarga = almacenCarga;
            this.almacenFin = almacenFin;
            this.carga = carga;
            this.distanciaKm = distanciaKm;
        }

        /** Primer índice de la secuencia que entrega el viaje. */
        public int getDesde() {
            return desde;
        }

        /** Índice siguiente al último pedido que entrega el viaje. */
        public int getHasta() {
            return hasta;
        }

        /**
         * Minuto en que la unidad queda comprometida con el viaje: el inicio de la ruta para el
         * primero, la llegada al almacén de recarga para los siguientes.
         */
        public int getSalida() {
            return salida;
        }

        /** Llegada al almacén en que termina el viaje; desde allí la unidad queda libre. */
        public int getFin() {
            return fin;
        }

        public Almacen getAlmacenCarga() {
            return almacenCarga;
        }

        public Almacen getAlmacenFin() {
            return almacenFin;
        }

        public int getCarga() {
            return carga;
        }

        public double getDistanciaKm() {
            return distanciaKm;
        }
    }

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private final List<Pedido> secuencia = new ArrayList<>();

    // ---- Atributos derivados, recalculados por recalcular() ----
    private boolean calculada = false;
    private double distanciaKm;
    private int[] minutosLlegada = new int[0];
    /** Salida (minuto con fracción) del tramo que llega al pedido i. */
    private double[] salidasTramo = new double[0];
    /** Punto de partida del tramo que llega al pedido i (destino anterior o almacén). */
    private Coordenada[] origenesTramo = new Coordenada[0];
    private int minutoInicio;
    private int minutoRetorno;
    private int cargaTotal;
    private boolean factible = true;
    private Almacen almacenRetorno;
    private int minutoInicioAlimentacion = -1;
    /** Entrega tras la cual se toma la alimentación (−1: en el almacén de origen). */
    private int indiceAlimentacion = Integer.MIN_VALUE;
    private String motivoInfactibilidad;
    private List<Viaje> viajes = Collections.emptyList();
    private Map<Almacen, Integer> consumo = Collections.emptyMap();

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
        r.origenesTramo = origenesTramo.clone();
        r.minutoInicio = minutoInicio;
        r.minutoRetorno = minutoRetorno;
        r.cargaTotal = cargaTotal;
        r.factible = factible;
        r.almacenRetorno = almacenRetorno;
        r.minutoInicioAlimentacion = minutoInicioAlimentacion;
        r.indiceAlimentacion = indiceAlimentacion;
        r.motivoInfactibilidad = motivoInfactibilidad;
        r.viajes = viajes;                      // inmutable tras recalcular
        r.consumo = consumo;                    // inmutable tras recalcular
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

    /** Indica si la ruta ya transporta alguna parte del mismo pedido registrado. */
    public boolean contienePedido(Pedido pedido) {
        Pedido original = pedido.getOriginal();
        for (Pedido p : secuencia) {
            if (p.getOriginal() == original) {
                return true;
            }
        }
        return false;
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
        indiceAlimentacion = Integer.MIN_VALUE;
        minutosLlegada = new int[secuencia.size()];
        salidasTramo = new double[secuencia.size()];
        origenesTramo = new Coordenada[secuencia.size()];
        viajes = Collections.emptyList();
        consumo = Collections.emptyMap();

        int registroMasTardio = Integer.MIN_VALUE;
        for (Pedido p : secuencia) {
            cargaTotal += p.getCantidad();
            registroMasTardio = Math.max(registroMasTardio, p.getMinutoRegistro());
        }
        int capacidad = vehiculo.getCapacidad();
        if (!par.permitirRecargas && cargaTotal > capacidad) {
            infactible("capacidad excedida (" + cargaTotal + ">" + capacidad + ")");
        }
        for (Pedido p : secuencia) {
            if (p.getCantidad() > capacidad) {
                infactible("capacidad excedida (" + p.getCantidad() + ">" + capacidad + ")");
            }
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

        boolean[] recargaAntes = cortesDeViaje(par.permitirRecargas);
        int nViajes = 1;
        for (boolean corte : recargaAntes) {
            if (corte) {
                nViajes++;
            }
        }
        if (nViajes > par.maxViajesPorRuta) {
            infactible("demasiados viajes (" + nViajes + ">" + par.maxViajesPorRuta + ")");
        }

        // Primera pasada sin alimentación: sirve para ubicar el mejor momento de tomarla.
        Recorrido sinAlimentacion = simular(ctx, recargaAntes, Integer.MIN_VALUE, 0);
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
                definitivo = simular(ctx, recargaAntes, posicion, inicioComida);
                if (!definitivo.alcanzable) {
                    infactible(definitivo.motivo);
                    minutoRetorno = Integer.MAX_VALUE;
                    return;
                }
                minutoInicioAlimentacion = inicioComida;
                indiceAlimentacion = posicion;
            }
        }

        distanciaKm = definitivo.distancia;
        minutosLlegada = definitivo.llegada;
        salidasTramo = definitivo.salida;
        origenesTramo = definitivo.origen;
        almacenRetorno = definitivo.almacenRetorno;
        minutoRetorno = definitivo.minutoRetorno;
        viajes = Collections.unmodifiableList(definitivo.viajes);
        consumo = Collections.unmodifiableMap(definitivo.consumo);

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
    }

    /**
     * Reparte la secuencia en viajes: un pedido abre viaje nuevo cuando ya no cabe en la carga
     * del viaje en curso.
     *
     * @return {@code recargaAntes[i]} verdadero si la unidad recarga antes de entregar el pedido i
     */
    private boolean[] cortesDeViaje(boolean permitirRecargas) {
        boolean[] recargaAntes = new boolean[secuencia.size()];
        if (!permitirRecargas) {
            return recargaAntes;
        }
        int capacidad = vehiculo.getCapacidad();
        int carga = 0;
        for (int i = 0; i < secuencia.size(); i++) {
            int q = secuencia.get(i).getCantidad();
            if (i > 0 && carga + q > capacidad) {
                recargaAntes[i] = true;
                carga = 0;
            }
            carga += q;
        }
        return recargaAntes;
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
        Coordenada[] origen;
        double[] completado;
        double minutoEnOrigen;
        int minutoRetorno;
        Almacen almacenRetorno;
        List<Viaje> viajes = new ArrayList<>();
        Map<Almacen, Integer> consumo = new LinkedHashMap<>();
        boolean alcanzable = true;
        String motivo;
    }

    /**
     * Pasada de evaluación hacia adelante con CAMINO_MÁS_RÁPIDO en cada tramo.
     *
     * @param recargaAntes         cortes de viaje (ver {@link #cortesDeViaje})
     * @param posicionAlimentacion índice de la entrega tras la cual se inserta la hora de
     *                             alimentación, −1 para insertarla en el almacén de origen, o
     *                             {@link Integer#MIN_VALUE} para no insertarla
     * @param inicioAlimentacion   instante en que comienza la hora de alimentación
     */
    private Recorrido simular(ContextoPlanificacion ctx, boolean[] recargaAntes, int posicionAlimentacion,
                              int inicioAlimentacion) {
        ParametrosPlanificador par = ctx.getParametros();
        MapaUrbano mapa = ctx.getMapa();
        TipoVehiculo tipo = vehiculo.getTipo();
        double velocidad = tipo.getVelocidadKmH();
        int n = secuencia.size();

        Recorrido r = new Recorrido();
        r.llegada = new int[n];
        r.salida = new double[n];
        r.origen = new Coordenada[n];
        r.completado = new double[n];

        // Stock que la ruta aún puede tomar de cada almacén intermedio al elegir dónde recargar.
        Map<Almacen, Integer> stockRestante = new LinkedHashMap<>();
        for (Almacen a : ctx.getAlmacenes()) {
            stockRestante.put(a, ctx.stockInicial(a));
        }

        double t = minutoInicio;

        // Tramo inicial: de la posición actual de la unidad al almacén de origen.
        Coordenada pos = vehiculo.getPosicion();
        MapaUrbano.Tramo tramo = mapa.caminoMasRapido(pos, almacenOrigen.getUbicacion(), t, velocidad, false);
        if (tramo == null) {
            r.alcanzable = false;
            r.motivo = "almacén de origen inalcanzable";
            return r;
        }
        double distanciaViaje = tramo.km;
        r.distancia += tramo.km;
        t = tramo.llegada;
        pos = almacenOrigen.getUbicacion();
        r.minutoEnOrigen = t;

        int inicioViaje = 0;
        int salidaViaje = minutoInicio;
        Almacen cargaEn = almacenOrigen;
        int cargaViaje = cargaDesde(0, recargaAntes);
        tomarStock(r, stockRestante, cargaEn, cargaViaje);

        if (posicionAlimentacion == -1) {
            t = Math.max(t, inicioAlimentacion) + Turnos.DURACION_ALMUERZO_MIN;
        }

        for (int i = 0; i < n; i++) {
            Pedido p = secuencia.get(i);

            if (recargaAntes[i]) {
                // Vuelve a recargar al almacén más cercano con stock para el siguiente viaje.
                int cargaSiguiente = cargaDesde(i, recargaAntes);
                Almacen recarga = almacenDeRecarga(ctx, pos, cargaSiguiente, stockRestante);
                tramo = mapa.caminoMasRapido(pos, recarga.getUbicacion(), t, velocidad, false);
                if (tramo == null) {
                    r.alcanzable = false;
                    r.motivo = "recarga imposible en " + recarga.getId();
                    return r;
                }
                distanciaViaje += tramo.km;
                r.distancia += tramo.km;
                t = tramo.llegada;
                int llegadaRecarga = (int) Math.ceil(t - 1e-9);
                r.viajes.add(new Viaje(inicioViaje, i, salidaViaje, llegadaRecarga, cargaEn, recarga,
                        cargaViaje, distanciaViaje));
                pos = recarga.getUbicacion();
                inicioViaje = i;
                salidaViaje = llegadaRecarga;
                cargaEn = recarga;
                cargaViaje = cargaSiguiente;
                distanciaViaje = 0;
                tomarStock(r, stockRestante, cargaEn, cargaViaje);
            }

            r.salida[i] = t;
            r.origen[i] = pos;
            tramo = mapa.caminoMasRapido(pos, p.getDestino(), t, velocidad, false);
            if (tramo == null) {
                r.alcanzable = false;
                r.motivo = "desplazamiento imposible hacia " + p.getDestino();
                return r;
            }
            distanciaViaje += tramo.km;
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
        distanciaViaje += tramo.km;
        r.distancia += tramo.km;
        r.minutoRetorno = (int) Math.ceil(tramo.llegada - 1e-9);
        r.viajes.add(new Viaje(inicioViaje, n, salidaViaje, r.minutoRetorno, cargaEn, r.almacenRetorno,
                cargaViaje, distanciaViaje));
        return r;
    }

    /** Carga del viaje que empieza en el índice indicado. */
    private int cargaDesde(int inicio, boolean[] recargaAntes) {
        int carga = 0;
        for (int i = inicio; i < secuencia.size(); i++) {
            if (i > inicio && recargaAntes[i]) {
                break;
            }
            carga += secuencia.get(i).getCantidad();
        }
        return carga;
    }

    private static void tomarStock(Recorrido r, Map<Almacen, Integer> stockRestante, Almacen a, int carga) {
        r.consumo.merge(a, carga, Integer::sum);
        if (!a.esCentral()) {
            stockRestante.merge(a, -carga, Integer::sum);
        }
    }

    /**
     * Almacén de recarga: el más cercano (distancia de retícula) con stock suficiente para el
     * viaje. El central tiene inventario ilimitado, así que siempre hay uno.
     */
    private static Almacen almacenDeRecarga(ContextoPlanificacion ctx, Coordenada desde, int carga,
                                            Map<Almacen, Integer> stockRestante) {
        Almacen mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Almacen a : ctx.getAlmacenes()) {
            if (!a.esCentral() && stockRestante.getOrDefault(a, 0) < carga) {
                continue;
            }
            int d = desde.distanciaManhattan(a.getUbicacion());
            if (d < mejorDistancia) {
                mejorDistancia = d;
                mejor = a;
            }
        }
        return mejor != null ? mejor : ctx.getInstancia().getAlmacenCentral();
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
        for (int i = 0; i < secuencia.size(); i++) {
            Pedido p = secuencia.get(i);
            Coordenada anterior = origenesTramo[i] != null ? origenesTramo[i] : almacenOrigen.getUbicacion();
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

    /** Carga total de la ruta, sumando todos sus viajes. */
    public int getCargaTotal() {
        int carga = 0;
        for (Pedido p : secuencia) {
            carga += p.getCantidad();
        }
        return carga;
    }

    /** Viajes de la ruta según el último cálculo (uno si no recarga). */
    public List<Viaje> getViajes() {
        return viajes;
    }

    /** Unidades que la ruta toma del almacén indicado, sumando sus viajes. */
    public int consumoEn(Almacen almacen) {
        return consumo.getOrDefault(almacen, 0);
    }

    /**
     * Índice del viaje en que se toma la hora de alimentación, o −1 si la ruta no la incluye.
     */
    public int viajeDeAlimentacion() {
        if (minutoInicioAlimentacion < 0 || viajes.isEmpty()) {
            return -1;
        }
        if (indiceAlimentacion < 0) {
            return 0;
        }
        for (int k = 0; k < viajes.size(); k++) {
            if (indiceAlimentacion < viajes.get(k).getHasta()) {
                return k;
            }
        }
        return viajes.size() - 1;
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

    /** Utilización media de la capacidad por viaje, para el semáforo de carga del visualizador. */
    public double utilizacion() {
        int nViajes = Math.max(1, viajes.size());
        return getCargaTotal() / (double) (vehiculo.getCapacidad() * nViajes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(vehiculo.getCodigo()).append(" desde ").append(almacenOrigen.getId())
                .append(" carga=").append(cargaTotal).append(" (cap. ").append(vehiculo.getCapacidad())
                .append(" por viaje, ").append(Math.max(1, viajes.size())).append(" viaje(s))")
                .append(" km=").append(String.format("%.0f", distanciaKm))
                .append(" inicio=").append(Turnos.formatear(minutoInicio));
        int k = 0;
        for (int i = 0; i < secuencia.size(); i++) {
            if (k < viajes.size() && viajes.get(k).getDesde() == i) {
                Viaje v = viajes.get(k);
                sb.append("\n    [viaje ").append(k + 1).append(": carga ").append(v.getCarga())
                        .append(" en ").append(v.getAlmacenCarga().getId())
                        .append(k == 0 ? "" : " a las " + Turnos.formatear(v.getSalida())).append(']');
                k++;
            }
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
