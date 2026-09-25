package pe.pucp.paqrap.estricto.modelo;

/** Interseccion de la reticula: 71 x 51 nodos, incluyendo ambos bordes. */
public record Nodo(int x, int y) {
    public Nodo {
        if (x < 0 || x > 70 || y < 0 || y > 50)
            throw new IllegalArgumentException("Nodo fuera de [0,70] x [0,50]: " + x + "," + y);
    }

    public int manhattan(Nodo otro) {
        return Math.abs(x - otro.x) + Math.abs(y - otro.y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
