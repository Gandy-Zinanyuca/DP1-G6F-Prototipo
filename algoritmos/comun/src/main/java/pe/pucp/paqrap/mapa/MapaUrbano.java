package pe.pucp.paqrap.mapa;

import pe.pucp.paqrap.modelo.Bloqueo;
import pe.pucp.paqrap.modelo.Coordenada;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retícula urbana de PaqRap con bloqueos dependientes del tiempo.
 *
 * <h2>Estructura</h2>
 * <p>El mapa es una retícula de (70+1) x (50+1) nodos separados 1 km (LE037). Cada nodo tiene
 * a lo sumo cuatro arcos incidentes, todos de doble sentido (RNF03) y de longitud unitaria.
 * En lugar de materializar una lista de adyacencia, el estado de la red se guarda en un
 * arreglo {@code byte[]} de una posición por nodo: los cuatro bits bajos indican qué arcos
 * incidentes están cerrados (bit 0 = Este, 1 = Oeste, 2 = Norte, 3 = Sur). La representación
 * ocupa 3 621 bytes y permite comprobar la transitabilidad de un arco en tiempo constante.</p>
 *
 * <h2>Cálculo de distancias</h2>
 * <p>Como todos los arcos tienen peso 1, la distancia mínima entre dos nodos se obtiene con
 * una <b>BFS</b> desde el origen, que en una sola pasada de O(V+E) ≈ 14 000 operaciones
 * produce la distancia a <i>todos</i> los nodos del mapa. Por eso el mapa no cachea pares
 * origen-destino sino vectores de distancia completos por origen: los orígenes que el
 * planificador consulta repetidamente (almacenes, posiciones de unidades, nodos de clientes)
 * pagan una sola BFS por ciclo. Cuando no hay ningún bloqueo vigente se evita la BFS por
 * completo y se devuelve la distancia Manhattan.</p>
 *
 * <h2>Dependencia temporal</h2>
 * <p>Los bloqueos tienen intervalo de vigencia, de modo que la red cambia a lo largo del
 * horizonte. El planificador evalúa cada ciclo con la fotografía de la red vigente al inicio
 * del ciclo ({@link #fijarInstante(int)}) y vuelve a planificar cada 15 minutos simulados
 * (LE026); la dinámica del problema se absorbe en esa cadencia de replanificación y no dentro
 * de la evaluación de una ruta individual.</p>
 */
public class MapaUrbano {

    public static final int NUM_NODOS = (Coordenada.ANCHO_MAX + 1) * (Coordenada.ALTO_MAX + 1);
    private static final int LIMITE_CACHE_ORIGENES = 4096;

    /** Desplazamientos por dirección: 0 Este, 1 Oeste, 2 Norte, 3 Sur. */
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};
    private static final int[] OPUESTA = {1, 0, 3, 2};

    private final List<Bloqueo> bloqueos;

    /** Bits de arcos cerrados por nodo para el instante fijado. */
    private final byte[] arcosCerrados = new byte[NUM_NODOS];

    private int instanteActual = -1;
    private boolean hayBloqueosVigentes;
    private List<Bloqueo> vigentes = new ArrayList<>();

    /** Caché LRU de vectores de distancia por nodo origen, válida para el instante fijado. */
    private final Map<Integer, int[]> cacheDistancias =
            new LinkedHashMap<Integer, int[]>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, int[]> eldest) {
                    return size() > LIMITE_CACHE_ORIGENES;
                }
            };

    private final int[] colaBfs = new int[NUM_NODOS];

    public MapaUrbano(List<Bloqueo> bloqueos) {
        this.bloqueos = bloqueos;
    }

    public List<Bloqueo> getBloqueos() {
        return bloqueos;
    }

    public List<Bloqueo> getBloqueosVigentes() {
        return vigentes;
    }

    public int getInstanteActual() {
        return instanteActual;
    }

    /**
     * Fija la fotografía de la red vial correspondiente al minuto indicado: recalcula el
     * conjunto de arcos cerrados e invalida la caché de distancias. Es la primera llamada de
     * cada ciclo de planificación.
     */
    public void fijarInstante(int minuto) {
        if (minuto == instanteActual) {
            return;
        }
        instanteActual = minuto;
        Arrays.fill(arcosCerrados, (byte) 0);
        cacheDistancias.clear();
        vigentes = new ArrayList<>();

        for (Bloqueo b : bloqueos) {
            if (!b.vigenteEn(minuto)) {
                continue;
            }
            vigentes.add(b);
            for (int[] arco : b.arcosUnitarios()) {
                cerrarArco(arco[0], arco[1]);
            }
        }
        hayBloqueosVigentes = !vigentes.isEmpty();
    }

    private void cerrarArco(int nodoA, int nodoB) {
        if (nodoA < 0 || nodoB < 0 || nodoA >= NUM_NODOS || nodoB >= NUM_NODOS) {
            return;
        }
        Coordenada a = Coordenada.desdeIndice(nodoA);
        for (int d = 0; d < 4; d++) {
            Coordenada vecino = new Coordenada(a.getX() + DX[d], a.getY() + DY[d]);
            if (vecino.dentroDelMapa() && vecino.indice() == nodoB) {
                arcosCerrados[nodoA] |= (byte) (1 << d);
                arcosCerrados[nodoB] |= (byte) (1 << OPUESTA[d]);
                return;
            }
        }
    }

    /** Indica si el arco unitario que sale de {@code nodo} en la dirección {@code d} es transitable. */
    private boolean transitable(int nodo, int d) {
        return (arcosCerrados[nodo] & (1 << d)) == 0;
    }

    /**
     * Indica si el nodo está totalmente aislado por bloqueos vigentes. Un pedido cuyo destino
     * quede aislado no puede insertarse en ninguna ruta y debe quedar diferido al siguiente
     * ciclo de planificación.
     */
    public boolean nodoAislado(Coordenada c) {
        if (!hayBloqueosVigentes) {
            return false;
        }
        int nodo = c.indice();
        for (int d = 0; d < 4; d++) {
            Coordenada vecino = new Coordenada(c.getX() + DX[d], c.getY() + DY[d]);
            if (vecino.dentroDelMapa() && transitable(nodo, d)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Distancia mínima en kilómetros entre dos nodos evitando los tramos cerrados en el
     * instante fijado. Devuelve {@link Integer#MAX_VALUE} si el destino es inalcanzable.
     */
    public int distancia(Coordenada origen, Coordenada destino) {
        if (!hayBloqueosVigentes) {
            return origen.distanciaManhattan(destino);
        }
        int[] dist = distanciasDesde(origen);
        return dist[destino.indice()];
    }

    /**
     * Vector de distancias mínimas desde un origen a todos los nodos del mapa, calculado con
     * una BFS y memorizado mientras no cambie el instante fijado.
     */
    public int[] distanciasDesde(Coordenada origen) {
        int idxOrigen = origen.indice();
        int[] cacheado = cacheDistancias.get(idxOrigen);
        if (cacheado != null) {
            return cacheado;
        }
        int[] dist = bfs(idxOrigen);
        cacheDistancias.put(idxOrigen, dist);
        return dist;
    }

    private int[] bfs(int idxOrigen) {
        int[] dist = new int[NUM_NODOS];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[idxOrigen] = 0;

        int cabeza = 0;
        int cola = 0;
        colaBfs[cola++] = idxOrigen;

        while (cabeza < cola) {
            int nodo = colaBfs[cabeza++];
            int x = nodo % (Coordenada.ANCHO_MAX + 1);
            int y = nodo / (Coordenada.ANCHO_MAX + 1);
            int d0 = dist[nodo] + 1;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx < 0 || nx > Coordenada.ANCHO_MAX || ny < 0 || ny > Coordenada.ALTO_MAX) {
                    continue;
                }
                if (!transitable(nodo, d)) {
                    continue;
                }
                int vecino = ny * (Coordenada.ANCHO_MAX + 1) + nx;
                if (dist[vecino] > d0) {
                    dist[vecino] = d0;
                    colaBfs[cola++] = vecino;
                }
            }
        }
        return dist;
    }

    /**
     * Reconstruye el camino mínimo nodo a nodo entre dos puntos, para trazarlo en el
     * visualizador (LE040). Devuelve una lista vacía si el destino es inalcanzable.
     */
    public List<Coordenada> caminoMinimo(Coordenada origen, Coordenada destino) {
        List<Coordenada> camino = new ArrayList<>();
        if (!hayBloqueosVigentes) {
            // Sin bloqueos basta un camino monótono en L.
            int x = origen.getX();
            int y = origen.getY();
            camino.add(origen);
            while (x != destino.getX()) {
                x += Integer.signum(destino.getX() - x);
                camino.add(new Coordenada(x, y));
            }
            while (y != destino.getY()) {
                y += Integer.signum(destino.getY() - y);
                camino.add(new Coordenada(x, y));
            }
            return camino;
        }
        int[] dist = distanciasDesde(origen);
        if (dist[destino.indice()] == Integer.MAX_VALUE) {
            return camino;
        }
        // Descenso por gradiente sobre el vector de distancias desde el origen.
        Coordenada actual = destino;
        camino.add(actual);
        while (!actual.equals(origen)) {
            int nodo = actual.indice();
            for (int d = 0; d < 4; d++) {
                Coordenada vecino = new Coordenada(actual.getX() + DX[d], actual.getY() + DY[d]);
                if (!vecino.dentroDelMapa() || !transitable(nodo, d)) {
                    continue;
                }
                if (dist[vecino.indice()] == dist[nodo] - 1) {
                    actual = vecino;
                    camino.add(actual);
                    break;
                }
            }
        }
        java.util.Collections.reverse(camino);
        return camino;
    }

    /**
     * Indica si el camino mínimo entre dos nodos atraviesa algún tramo de los bloqueos
     * vigentes. Lo usa el operador de destrucción {@code blocked-arc removal} para identificar
     * los pedidos cuyas rutas quedaron comprometidas por un cierre sobrevenido.
     */
    public boolean rutaAfectadaPorBloqueo(Coordenada origen, Coordenada destino) {
        if (!hayBloqueosVigentes) {
            return false;
        }
        int real = distancia(origen, destino);
        return real == Integer.MAX_VALUE || real > origen.distanciaManhattan(destino);
    }
}
