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
 *   <li>Hora de alimentación obligatoria de 1 hora en cada turno, separada al menos 1 hora de
 *       los cambios de turno (LE018); ver abajo.</li>
 *   <li>Confinamiento al turno, si el parámetro lo exige (LE017).</li>
 *   <li>Sin mantenimiento preventivo en ningún día que abarque la ruta hasta el regreso.</li>
 *   <li>Regreso al almacén más cercano al finalizar (LE020).</li>
 * </ul>
 * <p>El inventario de los almacenes intermedios acopla varias rutas: lo valida la solución.</p>
 *
 * <h2>Hora de alimentación</h2>
 * <p>Cada chofer debe tomar una hora de alimentación por turno, que empiece dentro de la ventana
 * [inicio del turno + 1 h, fin del turno − 2 h] ({@link Turnos}). La ruta no puede impedirlo: por
 * cada turno cuya ventana abarca y en que la unidad aún no comió, la ruta incluye la comida, salvo
 * que regrese a tiempo para tomarla después en el almacén. El momento lo decide el planificador al
 * evaluar la ruta: prueba cada punto admisible —antes de salir (en tiempo ocioso de la unidad), en
 * el almacén de origen o tras cada entrega— y elige el que cumple los plazos con menor distancia,
 * regreso más temprano y mayor holgura; deja de probar en cuanto uno no perjudica la ruta más que
 * la propia hora de comida. Como depende de la ruta de cada unidad, las unidades no
 * comen todas a la vez. Si ningún punto es admisible, la ruta no es factible.</p>
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

    /** Posición de una comida tomada antes de que la unidad salga, en su tiempo ocioso. */
    public static final int ANTES_DE_SALIR = -2;
    /** Posición de una comida tomada en el almacén de origen, antes de la primera entrega. */
    public static final int EN_ORIGEN = -1;

    /** Hora de alimentación elegida para un turno. */
    public static final class Comida {
        private final int posicion;
        private final int inicio;
        private final int turno;

        Comida(int posicion, int inicio, int turno) {
            this.posicion = posicion;
            this.inicio = inicio;
            this.turno = turno;
        }

        /**
         * Entrega tras la cual se toma ({@code i}), o {@link #ANTES_DE_SALIR} /
         * {@link #EN_ORIGEN}.
         */
        public int getPosicion() {
            return posicion;
        }

        public int getInicio() {
            return inicio;
        }

        /** Inicio del turno al que corresponde. */
        public int getTurno() {
            return turno;
        }
    }

    /** Comida por planificar: punto de la ruta y ventana admisible de inicio. */
    private static final class Pausa {
        final int posicion;
        final int turno;
        final int ventanaIni;
        final int ventanaFin;

        Pausa(int posicion, int turno) {
            this.posicion = posicion;
            this.turno = turno;
            this.ventanaIni = turno + Turnos.SEPARACION_CAMBIO_TURNO_MIN;
            this.ventanaFin = turno + Turnos.DURACION_TURNO_MIN - Turnos.SEPARACION_CAMBIO_TURNO_MIN
                    - Turnos.DURACION_ALMUERZO_MIN;
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
    /** Horas de alimentación de la ruta, una por turno que la requiere, en orden. */
    private List<Comida> comidas = Collections.emptyList();
    /**
     * Llegadas sin ninguna hora de alimentación: cota inferior de las llegadas de la ruta con
     * cualquier ubicación de las comidas (las comidas solo retrasan).
     */
    private int[] llegadasSinComida = new int[0];
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
        r.comidas = comidas;                    // inmutable tras recalcular
        r.llegadasSinComida = llegadasSinComida; // no se modifica tras recalcular
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
        comidas = Collections.emptyList();
        llegadasSinComida = new int[0];
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

        int finTurno = Turnos.finTurno(minutoInicio);

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

        // Primera pasada sin alimentación: cota inferior de las llegadas y punto de partida para
        // ubicar las comidas.
        List<Pausa> pausas = new ArrayList<>();
        Recorrido definitivo = simular(ctx, recargaAntes, pausas);
        if (!definitivo.alcanzable) {
            infactible(definitivo.motivo);
            minutoRetorno = Integer.MAX_VALUE;
            return;
        }
        llegadasSinComida = definitivo.llegada;

        // Una comida por cada turno cuya ventana la ruta ocupa, en orden cronológico. Agregar la
        // comida de un turno retrasa el regreso, así que la condición del ciclo se reevalúa. Si la
        // ruta ya llega tarde sin comer, ninguna comida la arregla: no se buscan (la verificación
        // de plazos la declara no factible).
        boolean llegaTardeSinComer = atraso(definitivo) > 0;
        for (int turno = Turnos.inicioTurno(minutoInicio);
             !llegaTardeSinComer && turno <= definitivo.minutoRetorno;
             turno += Turnos.DURACION_TURNO_MIN) {
            Pausa ventana = new Pausa(Integer.MIN_VALUE, turno);
            if (vehiculo.getTurnoDeUltimaAlimentacion() >= turno
                    || definitivo.minutoRetorno <= ventana.ventanaFin) {
                continue;   // ya comió en este turno, o regresa a tiempo de comer en el almacén
            }
            Recorrido mejor = null;
            Pausa elegida = null;
            for (Pausa candidata : candidatas(definitivo, pausas, turno)) {
                pausas.add(candidata);
                Recorrido prueba = simular(ctx, recargaAntes, pausas);
                pausas.remove(pausas.size() - 1);
                if (prueba.alcanzable && (mejor == null || esMejor(prueba, mejor))) {
                    mejor = prueba;
                    elegida = candidata;
                    if (sinPerjuicio(prueba, definitivo)) {
                        break;   // ningún otro punto puede mejorarla de forma relevante
                    }
                }
            }
            if (mejor == null) {
                infactible("sin hora de alimentación posible en el turno de las "
                        + Turnos.formatear(turno));
                continue;
            }
            pausas.add(elegida);
            definitivo = mejor;
        }

        minutoInicio = definitivo.salidaReal;
        comidas = Collections.unmodifiableList(definitivo.comidas);
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
     * Puntos de la ruta donde puede tomarse la comida del turno, dado el recorrido con las
     * comidas ya ubicadas: antes de salir (solo si es la primera comida), en el almacén de origen
     * y tras cada entrega, siempre después de la última comida ubicada. Un punto es admisible si
     * la comida puede empezar dentro de la ventana del turno.
     *
     * <p>De los puntos en que la unidad queda libre antes de que abra la ventana solo se conserva
     * el último: todos esperarían a la apertura, y seguir entregando mientras tanto no retrasa a
     * nadie.</p>
     *
     * <p>Orden de prueba: primero la comida antes de salir (en tiempo ocioso puede no retrasar
     * nada) y luego del punto más tardío al más temprano, porque comer tarde no retrasa las
     * entregas previas. Así la primera candidata {@link #sinPerjuicio} suele aparecer enseguida
     * y corta la búsqueda.</p>
     */
    private List<Pausa> candidatas(Recorrido base, List<Pausa> ubicadas, int turno) {
        int desde = ubicadas.isEmpty() ? ANTES_DE_SALIR : ubicadas.get(ubicadas.size() - 1).posicion;
        Pausa ventana = new Pausa(Integer.MIN_VALUE, turno);
        List<Pausa> lista = new ArrayList<>();
        Pausa enEspera = null;
        for (int pos = Math.max(desde, ANTES_DE_SALIR); pos < secuencia.size(); pos++) {
            double libre;
            if (pos == ANTES_DE_SALIR) {
                if (!ubicadas.isEmpty()) {
                    continue;
                }
                libre = vehiculo.getMinutoDisponibleDesde();
            } else if (pos == EN_ORIGEN) {
                libre = base.minutoEnOrigen;
            } else {
                libre = base.completado[pos];
            }
            if (Math.max(libre, ventana.ventanaIni) > ventana.ventanaFin) {
                break;   // los puntos siguientes quedan libres aún más tarde
            }
            if (libre <= ventana.ventanaIni) {
                enEspera = new Pausa(pos, turno);
            } else {
                lista.add(new Pausa(pos, turno));
            }
        }
        if (enEspera != null) {
            lista.add(0, enEspera);
        }
        List<Pausa> orden = new ArrayList<>(lista.size());
        if (!lista.isEmpty() && lista.get(0).posicion == ANTES_DE_SALIR) {
            orden.add(lista.remove(0));
        }
        for (int i = lista.size() - 1; i >= 0; i--) {
            orden.add(lista.get(i));
        }
        return orden;
    }

    /**
     * Indica si la comida no perjudica la ruta más de lo inevitable: sin atrasos, sin más
     * distancia y con el regreso retrasado a lo sumo la hora de la comida. Otro punto solo podría
     * mejorarla absorbiendo parte de esa hora en una espera, y la diferencia no justifica
     * seguir simulando.
     */
    private boolean sinPerjuicio(Recorrido conComida, Recorrido sinComida) {
        return atraso(conComida) == 0
                && conComida.distancia <= sinComida.distancia + 1e-6
                && conComida.minutoRetorno <= sinComida.minutoRetorno + Turnos.DURACION_ALMUERZO_MIN;
    }

    /**
     * Orden entre recorridos con distinta ubicación de las comidas: menos atraso total, luego
     * menor distancia, regreso más temprano y mayor holgura mínima.
     */
    private boolean esMejor(Recorrido a, Recorrido b) {
        long atrasoA = atraso(a);
        long atrasoB = atraso(b);
        if (atrasoA != atrasoB) {
            return atrasoA < atrasoB;
        }
        if (Math.abs(a.distancia - b.distancia) > 1e-6) {
            return a.distancia < b.distancia;
        }
        if (a.minutoRetorno != b.minutoRetorno) {
            return a.minutoRetorno < b.minutoRetorno;
        }
        return holguraMinima(a) > holguraMinima(b);
    }

    private long atraso(Recorrido r) {
        long total = 0;
        for (int i = 0; i < secuencia.size(); i++) {
            total += Math.max(0, r.llegada[i] - secuencia.get(i).getMinutoLimite());
        }
        return total;
    }

    private int holguraMinima(Recorrido r) {
        int minima = Integer.MAX_VALUE;
        for (int i = 0; i < secuencia.size(); i++) {
            minima = Math.min(minima, secuencia.get(i).getMinutoLimite() - r.llegada[i]);
        }
        return minima;
    }

    /** Resultado de una pasada de evaluación hacia adelante sobre la secuencia. */
    private static final class Recorrido {
        /** Momento en que la unidad deja su posición, tras una comida previa a la salida. */
        int salidaReal;
        List<Comida> comidas = new ArrayList<>();
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

        double finUltimaComida() {
            return comidas.get(comidas.size() - 1).inicio + Turnos.DURACION_ALMUERZO_MIN;
        }
    }

    /**
     * Toma la comida en cuanto la unidad queda libre, sin adelantarse a la ventana.
     *
     * @return falso (y el recorrido queda no alcanzable) si ya no puede empezar dentro de ella
     */
    private static boolean tomarComida(Recorrido r, Pausa pausa, double libre) {
        int inicio = (int) Math.ceil(Math.max(libre, pausa.ventanaIni) - 1e-9);
        if (inicio > pausa.ventanaFin) {
            r.alcanzable = false;
            r.motivo = "alimentación fuera de la ventana del turno de las " + Turnos.formatear(pausa.turno);
            return false;
        }
        r.comidas.add(new Comida(pausa.posicion, inicio, pausa.turno));
        return true;
    }

    /**
     * Pasada de evaluación hacia adelante con CAMINO_MÁS_RÁPIDO en cada tramo.
     *
     * @param recargaAntes cortes de viaje (ver {@link #cortesDeViaje})
     * @param pausas       comidas a tomar, en orden de posición; cada una empieza en cuanto la
     *                     unidad queda libre en ese punto, pero no antes de que abra su ventana.
     *                     Si alguna no puede empezar antes de que cierre, el recorrido no es
     *                     alcanzable.
     */
    private Recorrido simular(ContextoPlanificacion ctx, boolean[] recargaAntes, List<Pausa> pausas) {
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
        int k = 0;

        // Comida antes de salir: en el tiempo ocioso de la unidad, que está libre desde que quedó
        // disponible (puede ser anterior a T) y aún no parte.
        while (k < pausas.size() && pausas.get(k).posicion == ANTES_DE_SALIR) {
            if (!tomarComida(r, pausas.get(k++), vehiculo.getMinutoDisponibleDesde())) {
                return r;
            }
            t = Math.max(t, r.finUltimaComida());
        }
        r.salidaReal = (int) Math.ceil(t - 1e-9);

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
        int salidaViaje = r.salidaReal;
        Almacen cargaEn = almacenOrigen;
        int cargaViaje = cargaDesde(0, recargaAntes);
        tomarStock(r, stockRestante, cargaEn, cargaViaje);

        while (k < pausas.size() && pausas.get(k).posicion == EN_ORIGEN) {
            if (!tomarComida(r, pausas.get(k++), t)) {
                return r;
            }
            t = r.finUltimaComida();
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

            while (k < pausas.size() && pausas.get(k).posicion == i) {
                if (!tomarComida(r, pausas.get(k++), t)) {
                    return r;
                }
                t = r.finUltimaComida();
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
     * Turno de la última comida que se toma dentro de los primeros {@code nViajes} viajes de la
     * ruta (las previas a la salida cuentan en el primero), o {@link Integer#MIN_VALUE} si no hay
     * ninguna. El simulador lo usa para registrar en qué turno ya comió la unidad.
     */
    public int turnoDeAlimentacionEnViajes(int nViajes) {
        int turno = Integer.MIN_VALUE;
        for (Comida c : comidasEnViajes(nViajes)) {
            turno = Math.max(turno, c.turno);
        }
        return turno;
    }

    /** Comidas que se toman dentro de los primeros {@code nViajes} viajes (o antes de salir). */
    public List<Comida> comidasEnViajes(int nViajes) {
        if (nViajes <= 0 || viajes.isEmpty()) {
            return Collections.emptyList();
        }
        int hasta = viajes.get(Math.min(nViajes, viajes.size()) - 1).getHasta();
        List<Comida> lista = new ArrayList<>();
        for (Comida c : comidas) {
            if (c.posicion < hasta) {
                lista.add(c);
            }
        }
        return lista;
    }

    public boolean esFactible() {
        return factible;
    }

    public Almacen getAlmacenRetorno() {
        return almacenRetorno;
    }

    /** Horas de alimentación que incluye la ruta, en orden. */
    public List<Comida> getComidas() {
        return comidas;
    }

    /** Llegadas a cada destino si la ruta no incluyera comidas: cota inferior de las reales. */
    public int[] getLlegadasSinComida() {
        return llegadasSinComida;
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
        for (Comida c : comidas) {
            String donde = c.posicion == ANTES_DE_SALIR ? "antes de salir"
                    : c.posicion == EN_ORIGEN ? "en el almacén de origen"
                    : "tras entregar " + secuencia.get(c.posicion);
            sb.append("\n    (alimentación ").append(Turnos.formatear(c.inicio)).append(", ")
                    .append(donde).append(')');
        }
        sb.append("\n    retorno ").append(almacenRetorno == null ? "?" : almacenRetorno.getId())
                .append(' ').append(Turnos.formatear(minutoRetorno));
        if (!esFactible()) {
            sb.append("  [NO FACTIBLE: ").append(motivoInfactibilidad).append(']');
        }
        return sb.toString();
    }
}
