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

    /** Intervalos de cierre por calle, ordenados por inicio: {inicio, fin} en minutos. */
    private final Map<Long, List<int[]>> cierresPorArco = new HashMap<>();

    /** Intervalos de todos los bloqueos, para descartar Dijkstra cuando no hay ninguno activo. */
    private int[][] intervalosGlobales;

    private int instanteActual = -1;
    private List<Bloqueo> vigentes = new ArrayList<>();

    private final Map<String, Tramo> cache = new LinkedHashMap<String, Tramo>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tramo> eldest) {
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
        cierresPorArco.clear();
        cache.clear();
        instanteActual = -1;
        intervalosGlobales = new int[bloqueos.size()][];
        for (int i = 0; i < bloqueos.size(); i++) {
            Bloqueo b = bloqueos.get(i);
            int[] intervalo = {b.getMinutoInicio(), b.getMinutoFin()};
            intervalosGlobales[i] = intervalo;
            for (int[] arco : b.arcosUnitarios()) {
                cierresPorArco.computeIfAbsent(clave(arco[0], arco[1]), k -> new ArrayList<>())
                        .add(intervalo);
            }
        }
        for (List<int[]> lista : cierresPorArco.values()) {
            lista.sort((a, b) -> Integer.compare(a[0], b[0]));
        }
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
        List<int[]> cierres = cierresPorArco.get(clave(a, b));
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
        for (int[] c : intervalosGlobales) {
            if (c[0] < hasta && c[1] > desde) {
                return true;
            }
        }
        return false;
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

        String k = fuente + ":" + meta + ":" + Double.doubleToLongBits(salida) + ":"
                + Double.doubleToLongBits(velocidadKmH) + ":" + conArcos;
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

        // Etiqueta: {llegada, km, nodo}; orden por llegada, luego km, luego nodo (determinista).
        PriorityQueue<double[]> cola = new PriorityQueue<>((p, q) -> {
            int c = Double.compare(p[0], q[0]);
            if (c != 0) {
                return c;
            }
            c = Double.compare(p[1], q[1]);
            return c != 0 ? c : Double.compare(p[2], q[2]);
        });
        cola.add(new double[]{salida, 0, fuente});

        while (!cola.isEmpty()) {
            double[] actual = cola.poll();
            int u = (int) actual[2];
            if (actual[0] != llegada[u] || (int) actual[1] != distancia[u]) {
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
                double fin = proximaSalida(u, v, actual[0], cruce) + cruce;
                int km = distancia[u] + 1;
                if (fin < llegada[v] || (fin == llegada[v] && km < distancia[v])) {
                    llegada[v] = fin;
                    distancia[v] = km;
                    previo[v] = u;
                    cola.add(new double[]{fin, km, v});
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
