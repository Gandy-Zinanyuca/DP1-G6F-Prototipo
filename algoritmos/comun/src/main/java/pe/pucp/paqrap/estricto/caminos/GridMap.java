package pe.pucp.paqrap.estricto.caminos;

import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Calles unitarias bidireccionales e intervalos de bloqueo indexados por arista. */
public final class GridMap {
    public static final int ANCHO = 71, ALTO = 51, NUM_NODOS = ANCHO * ALTO;
    private record Intervalo(LocalDateTime inicio, LocalDateTime fin) { }
    private final Map<Long, List<Intervalo>> bloqueos = new HashMap<>();
    private final Map<Nodo, List<Intervalo>> nodosBloqueados = new HashMap<>();
    public GridMap(List<Bloqueo> datos) {
        this(datos, false);
    }
    public GridMap(List<Bloqueo> datos, boolean bloquearNodos) {
        for (Bloqueo bloqueo : datos) {
            Intervalo intervalo = new Intervalo(bloqueo.inicio(), bloqueo.fin());
            var nodos = new HashSet<Nodo>();
            for (int i = 1; i < bloqueo.puntos().size(); i++) {
                Nodo a = bloqueo.puntos().get(i - 1), b = bloqueo.puntos().get(i);
                int dx = Integer.compare(b.x(), a.x()), dy = Integer.compare(b.y(), a.y());
                while (!a.equals(b)) {
                    nodos.add(a);
                    Nodo siguiente = new Nodo(a.x() + dx, a.y() + dy);
                    bloqueos.computeIfAbsent(clave(a, siguiente), k -> new ArrayList<>())
                            .add(intervalo);
                    a = siguiente;
                }
                nodos.add(b);
            }
            if (bloquearNodos) for (Nodo n : nodos) {
                nodosBloqueados.computeIfAbsent(n, k -> new ArrayList<>()).add(intervalo);
                // Cierre conservador de todas las calles incidentes: no atraviesa ni gira por el nodo.
                for (Nodo vecino : vecinos(n)) bloqueos.computeIfAbsent(clave(n, vecino), k -> new ArrayList<>()).add(intervalo);
            }
        }
        for (var lista : bloqueos.values()) lista.sort(Comparator.comparing(Intervalo::inicio));
        for (var lista : nodosBloqueados.values()) lista.sort(Comparator.comparing(Intervalo::inicio));
    }
    public LocalDateTime proximaDisponibilidad(Nodo nodo, LocalDateTime tiempo) {
        LocalDateTime libre = tiempo;
        for (Intervalo i : nodosBloqueados.getOrDefault(nodo, List.of())) {
            if (libre.isBefore(i.inicio())) break;
            if (libre.isBefore(i.fin())) libre = i.fin();
        }
        return libre;
    }
    public static int indice(Nodo n) { return n.y() * ANCHO + n.x(); }
    public static Nodo nodo(int indice) { return new Nodo(indice % ANCHO, indice / ANCHO); }
    private static long clave(Nodo a, Nodo b) {
        int i = indice(a), j = indice(b);
        return (long) Math.min(i, j) * NUM_NODOS + Math.max(i, j);
    }
    public List<Nodo> vecinos(Nodo n) {
        var resultado = new ArrayList<Nodo>(4);
        if (n.x() > 0) resultado.add(new Nodo(n.x() - 1, n.y()));
        if (n.x() < 70) resultado.add(new Nodo(n.x() + 1, n.y()));
        if (n.y() > 0) resultado.add(new Nodo(n.x(), n.y() - 1));
        if (n.y() < 50) resultado.add(new Nodo(n.x(), n.y() + 1));
        return resultado;
    }
    /** El vehiculo debe poder recorrer toda la arista sin solapar un bloqueo. */
    public LocalDateTime proximaSalida(Nodo a, Nodo b, LocalDateTime llegada, Duration cruce) {
        if (a.manhattan(b) != 1 || cruce.isNegative() || cruce.isZero())
            throw new IllegalArgumentException("Se requiere una calle unitaria y duracion positiva");
        LocalDateTime salida = llegada;
        for (Intervalo intervalo : bloqueos.getOrDefault(clave(a, b), List.of())) {
            if (!salida.isBefore(intervalo.fin())) continue;
            if (!salida.plus(cruce).isAfter(intervalo.inicio())) break;
            salida = intervalo.fin();
        }
        return salida;
    }
    public boolean cruceValido(Nodo a, Nodo b, LocalDateTime salida, LocalDateTime llegada) {
        if (a.manhattan(b) != 1 || !salida.isBefore(llegada)) return false;
        return proximaSalida(a, b, salida, Duration.between(salida, llegada)).equals(salida);
    }
}
