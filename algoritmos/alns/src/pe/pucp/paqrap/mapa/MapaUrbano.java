package pe.pucp.paqrap.mapa;

import pe.pucp.paqrap.modelo.Bloqueo;
import pe.pucp.paqrap.modelo.Coordenada;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Retícula urbana de PaqRap con bloqueos dependientes del tiempo.
 *
 * <h2>Estructura</h2>
 * <p>El mapa es una retícula de (70+1) x (50+1) nodos separados 1 km (LE037). Cada nodo tiene
 * a lo sumo cuatro calles incidentes, todas de doble sentido (RNF03) y de longitud unitaria.
 * No se permiten movimientos diagonales.</p>
 *
 * <h2>CAMINO_MÁS_RÁPIDO (ISA, sección 5.1)</h2>
 * <p>Cada calle unitaria guarda los intervalos [inicio, fin) de los bloqueos que la cierran,
 * tal como vienen en el archivo de bloqueos. {@link #caminoMasRapido} es un Dijkstra temporal
 * ordenado por hora de llegada: un tramo solo puede recorrerse si el cruce completo no se
 * solapa con un intervalo de bloqueo; si se solapa, la unidad espera hasta la primera hora en
 * que puede cruzarlo. Como todo bloqueo tiene fin, siempre existe camino.</p>
 *
 * <p>Si el camino Manhattan canónico (primero en X, luego en Y) no requiere ninguna espera, es
 * óptimo —su llegada es la cota inferior— y se devuelve sin ejecutar Dijkstra.</p>
 *
 * <p>La búsqueda es A*: la cola se ordena por llegada + distancia Manhattan restante × tiempo de
 * cruce, una cota inferior consistente (las esperas solo retrasan), así que devuelve la misma
 * llegada óptima que Dijkstra explorando menos nodos. Los cierres se indexan por calle en un
 * arreglo y los intervalos de bloqueo se consultan por búsqueda binaria.</p>
 */
public class MapaUrbano {

    public static final int ANCHO = Coordenada.ANCHO_MAX + 1;
    public static final int NUM_NODOS = ANCHO * (Coordenada.ALTO_MAX + 1);
    private static final int LIMITE_CACHE = 50_000;

    /** Desplazamientos por dirección: 0 Este, 1 Oeste, 2 Norte, 3 Sur. */
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    /** Resultado de CAMINO_MÁS_RÁPIDO para un tramo. */
    public static final class Tramo {
        /** Hora de llegada al destino, en minutos (con fracción). */
        public final double llegada;
        /** Kilómetros recorridos. */
        public final int km;
        /** Calles recorridas, como claves {@link #clave(int, int)}; vacío si no se pidieron. */
        public final List<Long> arcos;

        Tramo(double llegada, int km, List<Long> arcos) {
            this.llegada = llegada;
            this.km = km;
            this.arcos = arcos;
        }
    }

    private final List<Bloqueo> bloqueos;

    /**
     * Intervalos de cierre por calle, ordenados por inicio ({inicio, fin} en minutos), indexados
     * por {@link #indiceCalle}; {@code null} si la calle nunca se cierra.
     */
    private int[][][] cierresPorCalle = new int[2 * NUM_NODOS][][];

    /** Inicios de todos los bloqueos, ordenados, y el mayor fin entre los primeros k. */
    private int[] iniciosOrdenados = new int[0];
    private int[] maxFinPrefijo = new int[0];

    private int instanteActual = -1;
    private List<Bloqueo> vigentes = new ArrayList<>();

    /** Clave del caché de tramos: origen, destino, salida, velocidad y si se piden las calles. */
    private static final class ClaveTramo {
        final int fuente;
        final int meta;
        final long salida;
        final long velocidad;
        final boolean conArcos;
        final int hash;

        ClaveTramo(int fuente, int meta, double salida, double velocidad, boolean conArcos) {
            this.fuente = fuente;
            this.meta = meta;
            this.salida = Double.doubleToLongBits(salida);
            this.velocidad = Double.doubleToLongBits(velocidad);
            this.conArcos = conArcos;
            int h = fuente * 31 + meta;
            h = h * 31 + Long.hashCode(this.salida);
            h = h * 31 + Long.hashCode(this.velocidad);
            this.hash = h * 2 + (conArcos ? 1 : 0);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ClaveTramo)) {
                return false;
            }
            ClaveTramo c = (ClaveTramo) o;
            return fuente == c.fuente && meta == c.meta && salida == c.salida
                    && velocidad == c.velocidad && conArcos == c.conArcos;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private final Map<ClaveTramo, Tramo> cache = new LinkedHashMap<ClaveTramo, Tramo>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ClaveTramo, Tramo> eldest) {
            return size() > LIMITE_CACHE;
        }
    };

    public MapaUrbano(List<Bloqueo> bloqueos) {
        this.bloqueos = new ArrayList<>(bloqueos);
        indexarBloqueos();
    }

    /**
     * Incorpora bloqueos de un periodo posterior (la simulación encadena meses). Invalida los
     * caminos memorizados, que pudieron calcularse sin conocer estos cierres.
     */
    public void agregarBloqueos(List<Bloqueo> nuevos) {
        bloqueos.addAll(nuevos);
        indexarBloqueos();
    }

    /**
     * Descarta los bloqueos que terminaron antes del minuto indicado: ya no afectan ningún
     * camino futuro y solo encarecerían la búsqueda en simulaciones de varios meses.
     */
    public void descartarBloqueosTerminadosAntesDe(int minuto) {
        if (bloqueos.removeIf(b -> b.getMinutoFin() < minuto)) {
            indexarBloqueos();
        }
    }

    private void indexarBloqueos() {
        cache.clear();
        instanteActual = -1;
        Map<Integer, List<int[]>> porCalle = new HashMap<>();
        int[][] intervalos = new int[bloqueos.size()][];
        for (int i = 0; i < bloqueos.size(); i++) {
            Bloqueo b = bloqueos.get(i);
            int[] intervalo = {b.getMinutoInicio(), b.getMinutoFin()};
            intervalos[i] = intervalo;
            for (int[] arco : b.arcosUnitarios()) {
                porCalle.computeIfAbsent(indiceCalle(arco[0], arco[1]), k -> new ArrayList<>()).add(intervalo);
            }
        }
        cierresPorCalle = new int[2 * NUM_NODOS][][];
        for (Map.Entry<Integer, List<int[]>> e : porCalle.entrySet()) {
            List<int[]> lista = e.getValue();
            lista.sort((x, y) -> Integer.compare(x[0], y[0]));
            cierresPorCalle[e.getKey()] = lista.toArray(new int[0][]);
        }
        Arrays.sort(intervalos, (x, y) -> Integer.compare(x[0], y[0]));
        iniciosOrdenados = new int[intervalos.length];
        maxFinPrefijo = new int[intervalos.length];
        int maxFin = Integer.MIN_VALUE;
        for (int i = 0; i < intervalos.length; i++) {
            iniciosOrdenados[i] = intervalos[i][0];
            maxFin = Math.max(maxFin, intervalos[i][1]);
            maxFinPrefijo[i] = maxFin;
        }
    }

    /**
     * Índice de la calle unitaria entre dos nodos vecinos: cada nodo es dueño de su calle hacia
     * el Este (2·nodo) y hacia el Norte (2·nodo + 1).
     */
    private static int indiceCalle(int nodoA, int nodoB) {
        int menor = Math.min(nodoA, nodoB);
        int mayor = Math.max(nodoA, nodoB);
        return 2 * menor + (mayor - menor == 1 ? 0 : 1);
    }

    public List<Bloqueo> getBloqueos() {
        return bloqueos;
    }

    /** Bloqueos vigentes en el instante de planificación T. */
    public List<Bloqueo> getBloqueosVigentes() {
        return vigentes;
    }

    public int getInstanteActual() {
        return instanteActual;
    }

    /** Fija el instante de planificación T (solo determina qué bloqueos son "vigentes en T"). */
    public void fijarInstante(int minuto) {
        if (minuto == instanteActual) {
            return;
        }
        instanteActual = minuto;
        vigentes = new ArrayList<>();
        for (Bloqueo b : bloqueos) {
            if (b.vigenteEn(minuto)) {
                vigentes.add(b);
            }
        }
    }

    /** Calles bloqueadas vigentes en el instante indicado, como claves de arco. */
    public Set<Long> arcosBloqueadosEn(int minuto) {
        Set<Long> arcos = new HashSet<>();
        for (Bloqueo b : bloqueos) {
            if (b.vigenteEn(minuto)) {
                for (int[] arco : b.arcosUnitarios()) {
                    arcos.add(clave(arco[0], arco[1]));
                }
            }
        }
        return arcos;
    }

    /** Clave de una calle unitaria, independiente del sentido. */
    public static long clave(int nodoA, int nodoB) {
        return (long) Math.min(nodoA, nodoB) * NUM_NODOS + Math.max(nodoA, nodoB);
    }

    /**
     * Primera hora ≥ {@code llegada} en que puede iniciarse el cruce de la calle a–b de modo que
     * el cruce completo, de duración {@code cruce}, no se solape con ningún bloqueo.
     */
    private double proximaSalida(int a, int b, double llegada, double cruce) {
        int[][] cierres = cierresPorCalle[indiceCalle(a, b)];
        double salida = llegada;
        if (cierres == null) {
            return salida;
        }
        for (int[] c : cierres) {
            if (salida >= c[1]) {
                continue;
            }
            if (salida + cruce <= c[0]) {
                break;
            }
            salida = c[1];
        }
        return salida;
    }

    private boolean hayBloqueoEntre(double desde, double hasta) {
        // Algún bloqueo con inicio < hasta y fin > desde: entre los que empiezan antes de 'hasta'
        // (prefijo por búsqueda binaria), basta con que el mayor fin supere 'desde'.
        int lo = 0;
        int hi = iniciosOrdenados.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (iniciosOrdenados[mid] < hasta) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo > 0 && maxFinPrefijo[lo - 1] > desde;
    }

    /**
     * CAMINO_MÁS_RÁPIDO(origen, destino, salida, velocidad, bloqueos).
     *
     * @param salida       minuto (con fracción) en que la unidad parte del origen
     * @param velocidadKmH velocidad del tipo de unidad
     * @param conArcos     si es verdadero, reconstruye la secuencia de calles recorridas
     */
    public Tramo caminoMasRapido(Coordenada origen, Coordenada destino, double salida,
                                 double velocidadKmH, boolean conArcos) {
        double cruce = 60.0 / velocidadKmH;
        int fuente = origen.indice();
        int meta = destino.indice();
        int manhattan = origen.distanciaManhattan(destino);

        if (fuente == meta) {
            return new Tramo(salida, 0, Collections.emptyList());
        }

        // Sin bloqueos activos en la ventana de viaje, el camino Manhattan es óptimo.
        if (!hayBloqueoEntre(salida, salida + manhattan * cruce)) {
            return new Tramo(salida + manhattan * cruce, manhattan,
                    conArcos ? arcosManhattan(origen, destino) : Collections.emptyList());
        }

        ClaveTramo k = new ClaveTramo(fuente, meta, salida, velocidadKmH, conArcos);
        Tramo cacheado = cache.get(k);
        if (cacheado != null) {
            return cacheado;
        }
        Tramo t = recorridoCanonico(origen, destino, salida, cruce, conArcos);
        if (t == null) {
            t = dijkstraTemporal(fuente, meta, salida, cruce, conArcos);
        }
        cache.put(k, t);
        return t;
    }

    /** Camino en L sin esperas, o {@code null} si alguna calle obliga a esperar. */
    private Tramo recorridoCanonico(Coordenada origen, Coordenada destino, double salida,
                                    double cruce, boolean conArcos) {
        List<Long> arcos = conArcos ? new ArrayList<>() : Collections.emptyList();
        int x = origen.getX();
        int y = origen.getY();
        double hora = salida;
        int km = 0;
        while (x != destino.getX() || y != destino.getY()) {
            int nx = x;
            int ny = y;
            if (x != destino.getX()) {
                nx += Integer.signum(destino.getX() - x);
            } else {
                ny += Integer.signum(destino.getY() - y);
            }
            int a = y * ANCHO + x;
            int b = ny * ANCHO + nx;
            if (proximaSalida(a, b, hora, cruce) != hora) {
                return null;
            }
            if (conArcos) {
                arcos.add(clave(a, b));
            }
            hora += cruce;
            km++;
            x = nx;
            y = ny;
        }
        return new Tramo(hora, km, arcos);
    }

    private Tramo dijkstraTemporal(int fuente, int meta, double salida, double cruce,
                                   boolean conArcos) {
        double[] llegada = new double[NUM_NODOS];
        int[] distancia = new int[NUM_NODOS];
        int[] previo = new int[NUM_NODOS];
        Arrays.fill(llegada, Double.POSITIVE_INFINITY);
        Arrays.fill(previo, -1);
        llegada[fuente] = salida;

        // A*: etiqueta {llegada + cota, llegada, km, nodo}; la cota es la distancia Manhattan
        // restante × tiempo de cruce (consistente: las esperas por bloqueo solo retrasan).
        // Orden por estimación, luego km, luego nodo (determinista).
        final int mx = meta % ANCHO;
        final int my = meta / ANCHO;
        PriorityQueue<double[]> cola = new PriorityQueue<>((p, q) -> {
            int c = Double.compare(p[0], q[0]);
            if (c != 0) {
                return c;
            }
            c = Double.compare(p[2], q[2]);
            return c != 0 ? c : Double.compare(p[3], q[3]);
        });
        cola.add(new double[]{salida + cota(fuente, mx, my, cruce), salida, 0, fuente});

        while (!cola.isEmpty()) {
            double[] actual = cola.poll();
            int u = (int) actual[3];
            if (actual[1] != llegada[u] || (int) actual[2] != distancia[u]) {
                continue;
            }
            if (u == meta) {
                break;
            }
            int ux = u % ANCHO;
            int uy = u / ANCHO;
            for (int d = 0; d < 4; d++) {
                int nx = ux + DX[d];
                int ny = uy + DY[d];
                if (nx < 0 || nx > Coordenada.ANCHO_MAX || ny < 0 || ny > Coordenada.ALTO_MAX) {
                    continue;
                }
                int v = ny * ANCHO + nx;
                double fin = proximaSalida(u, v, actual[1], cruce) + cruce;
                int km = distancia[u] + 1;
                if (fin < llegada[v] || (fin == llegada[v] && km < distancia[v])) {
                    llegada[v] = fin;
                    distancia[v] = km;
                    previo[v] = u;
                    cola.add(new double[]{fin + cota(v, mx, my, cruce), fin, km, v});
                }
            }
        }
        if (Double.isInfinite(llegada[meta])) {
            return null;   // camino inexistente
        }
        List<Long> arcos = Collections.emptyList();
        if (conArcos) {
            arcos = new ArrayList<>();
            for (int v = meta; v != fuente; v = previo[v]) {
                arcos.add(clave(previo[v], v));
            }
            Collections.reverse(arcos);
        }
        return new Tramo(llegada[meta], distancia[meta], arcos);
    }

    /** Cota inferior del tiempo restante: distancia Manhattan al destino × tiempo de cruce. */
    private static double cota(int nodo, int mx, int my, double cruce) {
        return (Math.abs(nodo % ANCHO - mx) + Math.abs(nodo / ANCHO - my)) * cruce;
    }

    private static List<Long> arcosManhattan(Coordenada origen, Coordenada destino) {
        List<Long> arcos = new ArrayList<>();
        int x = origen.getX();
        int y = origen.getY();
        while (x != destino.getX() || y != destino.getY()) {
            int nx = x;
            int ny = y;
            if (x != destino.getX()) {
                nx += Integer.signum(destino.getX() - x);
            } else {
                ny += Integer.signum(destino.getY() - y);
            }
            arcos.add(clave(y * ANCHO + x, ny * ANCHO + nx));
            x = nx;
            y = ny;
        }
        return arcos;
    }
}
