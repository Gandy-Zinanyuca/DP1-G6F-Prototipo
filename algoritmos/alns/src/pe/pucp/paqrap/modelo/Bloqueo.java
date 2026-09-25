package pe.pucp.paqrap.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Cierre temporal de una secuencia de tramos de calle.
 *
 * <p>
 * Formato del archivo mensual de bloqueos:
 * {@code DDdHHhMMm-DDdHHhMMm:x1,y1,x2,y2,...,xn,yn}. Los pares consecutivos
 * describen una polilínea de nodos; cada par de nodos vecinos define un tramo
 * bloqueado en ambos sentidos (RNF03) durante el intervalo indicado (LE075).
 * </p>
 *
 * <p>
 * Los nodos de la polilínea siempre difieren en una sola componente, por lo que
 * el tramo entre dos vértices se expande en los arcos unitarios de 1 km que lo
 * componen. Esa expansión es la que consume el A* del mapa.
 * </p>
 */
public class Bloqueo {

    private final int minutoInicio;
    private final int minutoFin;
    private final List<Coordenada> vertices;

    public Bloqueo(int minutoInicio, int minutoFin, List<Coordenada> vertices) {
        this.minutoInicio = minutoInicio;
        this.minutoFin = minutoFin;
        this.vertices = vertices;
    }

    public int getMinutoInicio() {
        return minutoInicio;
    }

    public int getMinutoFin() {
        return minutoFin;
    }

    public List<Coordenada> getVertices() {
        return vertices;
    }

    public boolean vigenteEn(int minuto) {
        return minuto >= minutoInicio && minuto <= minutoFin;
    }

    /**
     * Duración del bloqueo en minutos, usada en los indicadores de incidencias
     * (LE085).
     */
    public int duracion() {
        return minutoFin - minutoInicio;
    }

    /**
     * Expande la polilínea en los arcos unitarios de 1 km que quedan cerrados. Cada
     * arco se devuelve como un par de índices lineales de nodo {@code {a, b}} con a
     * &lt; b, de modo que el par identifica el tramo sin importar el sentido de
     * circulación.
     */
    public List<int[]> arcosUnitarios() {
        List<int[]> arcos = new ArrayList<>();
        for (int i = 0; i + 1 < vertices.size(); i++) {
            Coordenada desde = vertices.get(i);
            Coordenada hasta = vertices.get(i + 1);
            int dx = Integer.signum(hasta.getX() - desde.getX());
            int dy = Integer.signum(hasta.getY() - desde.getY());
            if (dx != 0 && dy != 0) {
                // Tramo diagonal: no existe en la retícula, se ignora por robustez de lectura.
                continue;
            }
            int pasos = desde.distanciaManhattan(hasta);
            Coordenada actual = desde;
            for (int p = 0; p < pasos; p++) {
                Coordenada siguiente = new Coordenada(actual.getX() + dx, actual.getY() + dy);
                int a = actual.indice();
                int b = siguiente.indice();
                arcos.add(new int[] { Math.min(a, b), Math.max(a, b) });
                actual = siguiente;
            }
        }
        return arcos;
    }

    @Override
    public String toString() {
        return "Bloqueo[" + Turnos.formatear(minutoInicio) + "-" + Turnos.formatear(minutoFin) + " " + vertices.size()
                + " vértices]";
    }
}
