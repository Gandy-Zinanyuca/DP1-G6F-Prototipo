package pe.pucp.paqrap.modelo;

/**
 * Nodo de la retícula urbana de PaqRap.
 *
 * <p>El mapa es una retícula rectangular de 70 km (eje X) por 50 km (eje Y) con nodos
 * espaciados 1 km y origen (0,0) en la esquina inferior izquierda (LE037). Como todas las
 * calles son de doble sentido (RNF03) y solo existen tramos horizontales y verticales,
 * la distancia libre de bloqueos entre dos nodos es la distancia Manhattan.</p>
 *
 * <p>La clase es inmutable y se usa como clave de mapas: {@link #hashCode()} empaqueta
 * ambas componentes en un entero, lo que permite indexar la retícula en arreglos planos.</p>
 */
public final class Coordenada {

    public static final int ANCHO_MAX = 70;   // nodos 0..70 en X
    public static final int ALTO_MAX = 50;    // nodos 0..50 en Y

    private final int x;
    private final int y;

    public Coordenada(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /** Distancia Manhattan en kilómetros, válida cuando no hay bloqueos de por medio. */
    public int distanciaManhattan(Coordenada otra) {
        return Math.abs(this.x - otra.x) + Math.abs(this.y - otra.y);
    }

    /** Índice lineal del nodo dentro de la retícula; usado por el A* del mapa. */
    public int indice() {
        return y * (ANCHO_MAX + 1) + x;
    }

    public static Coordenada desdeIndice(int indice) {
        return new Coordenada(indice % (ANCHO_MAX + 1), indice / (ANCHO_MAX + 1));
    }

    public boolean dentroDelMapa() {
        return x >= 0 && x <= ANCHO_MAX && y >= 0 && y <= ALTO_MAX;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Coordenada)) {
            return false;
        }
        Coordenada c = (Coordenada) o;
        return x == c.x && y == c.y;
    }

    @Override
    public int hashCode() {
        return indice();
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
