package pe.pucp.paqrap.modelo;

/**
 * Almacén de producto P: uno central de inventario ilimitado y dos intermedios.
 *
 * <p>
 * Posiciones vigentes: central en (27,14), intermedio Nor-Oeste en (12,38) e
 * intermedio Este en (57,27). Los intermedios tienen capacidad configurable con
 * valor por defecto de 1 000 unidades (LE031) y se recargan de forma
 * instantánea a las 23:59:59 de cada día simulado (LE033). El central se trata
 * como inventario ilimitado (LE030).
 * </p>
 *
 * <p>
 * El planificador consume inventario de forma tentativa durante la construcción
 * de rutas: cada ruta descuenta del almacén de origen la suma de las cantidades
 * que carga, y ninguna asignación puede dejar el stock por debajo de cero
 * (LE019).
 * </p>
 */
public class Almacen {

    public static final int CAPACIDAD_INTERMEDIO_POR_DEFECTO = 1000;

    private final String id;
    private final Coordenada ubicacion;
    private final boolean central;
    private final int capacidad;

    private int stock;

    public Almacen(String id, Coordenada ubicacion, boolean central, int capacidad) {
        this.id = id;
        this.ubicacion = ubicacion;
        this.central = central;
        this.capacidad = capacidad;
        this.stock = central ? Integer.MAX_VALUE : capacidad;
    }

    /**
     * Construye la configuración estándar de los tres almacenes de PaqRap (LE049).
     */
    public static Almacen[] configuracionEstandar(int capacidadIntermedios) {
        return new Almacen[] { new Almacen("ALM-CENTRAL", new Coordenada(27, 14), true, Integer.MAX_VALUE),
                new Almacen("ALM-NOROESTE", new Coordenada(12, 38), false, capacidadIntermedios),
                new Almacen("ALM-ESTE", new Coordenada(57, 27), false, capacidadIntermedios) };
    }

    public String getId() {
        return id;
    }

    public Coordenada getUbicacion() {
        return ubicacion;
    }

    public boolean esCentral() {
        return central;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public int getStock() {
        return central ? Integer.MAX_VALUE : stock;
    }

    public boolean puedeAtender(int cantidad) {
        return central || stock >= cantidad;
    }

    /**
     * Descuenta unidades al asignar un pedido a una ruta (LE032); nunca deja stock
     * negativo.
     */
    public void descontar(int cantidad) {
        if (central) {
            return;
        }
        if (stock < cantidad) {
            throw new IllegalStateException("Stock insuficiente en " + id);
        }
        stock -= cantidad;
    }

    /**
     * Devuelve unidades al deshacer una asignación tentativa durante la búsqueda.
     */
    public void devolver(int cantidad) {
        if (central) {
            return;
        }
        stock = Math.min(capacidad, stock + cantidad);
    }

    /**
     * Recarga instantánea hasta la capacidad máxima a las 23:59:59 (LE033, LE034).
     */
    public void recargar() {
        if (!central) {
            stock = capacidad;
        }
    }

    /**
     * Nivel de semáforo del inventario (LE028, LE029). El almacén central, al ser
     * ilimitado, se reporta siempre en verde.
     *
     * @param umbralAmbar fracción de capacidad por debajo de la cual el nivel es
     *                    ámbar
     * @param umbralRojo  fracción de capacidad por debajo de la cual el nivel es
     *                    rojo
     */
    public String semaforo(double umbralAmbar, double umbralRojo) {
        if (central) {
            return "VERDE";
        }
        double ocupacion = stock / (double) capacidad;
        if (ocupacion < umbralRojo) {
            return "ROJO";
        }
        if (ocupacion < umbralAmbar) {
            return "AMBAR";
        }
        return "VERDE";
    }

    @Override
    public String toString() {
        return id + ubicacion + (central ? "[ilimitado]" : "[" + stock + "/" + capacidad + "]");
    }
}
