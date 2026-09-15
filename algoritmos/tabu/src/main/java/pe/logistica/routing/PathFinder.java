package pe.logistica.routing;

import pe.logistica.model.Nodo;
import pe.logistica.model.TipoVehiculo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Dijkstra temporal FIFO, un algoritmo exacto de caminos, no una metaheuristica.
 * Minimiza llegada y desempata por distancia; permite esperar a la reapertura.
 */
public final class PathFinder {
    private final GridMap mapa;
    public PathFinder(GridMap mapa) { this.mapa = Objects.requireNonNull(mapa); }
    private record Etiqueta(int nodo, LocalDateTime tiempo, int distancia) { }
    public Camino buscar(Nodo origen, Nodo destino, LocalDateTime inicio, TipoVehiculo tipo) {
        return buscar(origen, destino, inicio, tipo.velocidadKmh());
    }
    /** Lanza excepcion si la reticula no admite camino (no deberia ocurrir: todo bloqueo tiene fin). */
    public Camino buscar(Nodo origen, Nodo destino, LocalDateTime inicio, double velocidadKmh) {
        Camino camino = buscarOpcional(origen, destino, inicio, velocidadKmh);
        if (camino == null) throw new IllegalStateException("No existe camino en la reticula");
        return camino;
    }
    /** CAMINO_MAS_RAPIDO del pseudocodigo 5.1: devuelve null cuando no existe camino ("camino inexistente"). */
    public Camino buscarOpcional(Nodo origen, Nodo destino, LocalDateTime inicio, double velocidadKmh) {
        if (!Double.isFinite(velocidadKmh) || velocidadKmh < 1 || velocidadKmh > 300)
            throw new IllegalArgumentException("Velocidad fuera de [1,300] km/h");
        // Redondear hacia arriba evita prometer una llegada anterior a la fisicamente posible.
        Duration cruce = Duration.ofNanos((long) Math.ceil(3_600_000_000_000.0 / velocidadKmh));
        // Si el camino Manhattan canonico no requiere espera, ya es optimo.
        var directo = new ArrayList<PasoCamino>();
        LocalDateTime partida = mapa.proximaDisponibilidad(origen, inicio);
        Nodo n = origen; LocalDateTime hora = partida; boolean libre = true;
        while (!n.equals(destino)) {
            Nodo otro = n.x() != destino.x() ? new Nodo(n.x() + Integer.compare(destino.x(), n.x()), n.y())
                    : new Nodo(n.x(), n.y() + Integer.compare(destino.y(), n.y()));
            if (!mapa.proximaSalida(n, otro, hora, cruce).equals(hora)) { libre = false; break; }
            directo.add(new PasoCamino(n, otro, hora, hora.plus(cruce)));
            n = otro; hora = hora.plus(cruce);
        }
        if (libre) return new Camino(origen, inicio, hora, directo);

        LocalDateTime[] llegada = new LocalDateTime[GridMap.NUM_NODOS];
        int[] distancia = new int[GridMap.NUM_NODOS]; Arrays.fill(distancia, Integer.MAX_VALUE);
        PasoCamino[] anterior = new PasoCamino[GridMap.NUM_NODOS];
        var cola = new PriorityQueue<Etiqueta>(Comparator.comparing(Etiqueta::tiempo)
                .thenComparingInt(Etiqueta::distancia).thenComparingInt(Etiqueta::nodo));
        int fuente = GridMap.indice(origen), meta = GridMap.indice(destino);
        llegada[fuente] = partida; distancia[fuente] = 0; cola.add(new Etiqueta(fuente, partida, 0));
        while (!cola.isEmpty()) {
            Etiqueta actual = cola.remove(); int u = actual.nodo();
            if (!actual.tiempo().equals(llegada[u]) || actual.distancia() != distancia[u]) continue;
            if (u == meta) break;
            Nodo a = GridMap.nodo(u);
            for (Nodo b : mapa.vecinos(a)) {
                int v = GridMap.indice(b);
                LocalDateTime salida = mapa.proximaSalida(a, b, actual.tiempo(), cruce);
                LocalDateTime fin = salida.plus(cruce); int km = actual.distancia() + 1;
                if (llegada[v] == null || fin.isBefore(llegada[v]) || (fin.equals(llegada[v]) && km < distancia[v])) {
                    llegada[v] = fin; distancia[v] = km;
                    anterior[v] = new PasoCamino(a, b, salida, fin);
                    cola.add(new Etiqueta(v, fin, km));
                }
            }
        }
        // Todos los bloqueos tienen fin: en la practica siempre existe camino si se permite esperar.
        if (llegada[meta] == null) return null;
        var pasos = new ArrayList<PasoCamino>();
        for (int u = meta; u != fuente; ) {
            PasoCamino p = anterior[u]; pasos.add(p); u = GridMap.indice(p.origen());
        }
        Collections.reverse(pasos);
        return new Camino(origen, inicio, llegada[meta], pasos);
    }
}
