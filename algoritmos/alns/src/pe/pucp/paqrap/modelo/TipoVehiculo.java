package pe.pucp.paqrap.modelo;

/**
 * Tipos de unidad de transporte de la flota heterogénea de PaqRap.
 *
 * <p>Capacidades, velocidades y costos provienen del enunciado de la situación auténtica:
 * auto 24 paquetes / 40 km-h / S/ 8.00 por km; moto 8 / 25 / S/ 6.00; bicicleta 4 / 12 / S/ 3.00.
 * Los valores son mutables mediante {@link #setVelocidadKmH(double)} para satisfacer LE024
 * (velocidad promedio configurable por parámetro sin recompilar).</p>
 *
 * <p>El prefijo de código corresponde a la nomenclatura de los archivos de mantenimiento
 * preventivo: TA = auto, TM = moto, TB = bicicleta.</p>
 */
public enum TipoVehiculo {

    // Velocidades del perfil publicado (Flota!B3:D5, ver REFERENCIAS.md de algoritmos/tabu):
    // 40/25/12 fueron los valores de la demo legada, reemplazados por 20/40/14 el 08/09/2026.
    AUTO("TA", 24, 20.0, 8.00),
    MOTO("TM", 8, 40.0, 6.00),
    BICICLETA("TB", 4, 14.0, 3.00);

    private final String prefijo;
    private final int capacidad;
    private double velocidadKmH;
    private double costoPorKm;

    TipoVehiculo(String prefijo, int capacidad, double velocidadKmH, double costoPorKm) {
        this.prefijo = prefijo;
        this.capacidad = capacidad;
        this.velocidadKmH = velocidadKmH;
        this.costoPorKm = costoPorKm;
    }

    public String getPrefijo() {
        return prefijo;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public double getVelocidadKmH() {
        return velocidadKmH;
    }

    public void setVelocidadKmH(double velocidadKmH) {
        if (velocidadKmH <= 0) {
            throw new IllegalArgumentException("La velocidad debe ser positiva");
        }
        this.velocidadKmH = velocidadKmH;
    }

    public double getCostoPorKm() {
        return costoPorKm;
    }

    public void setCostoPorKm(double costoPorKm) {
        this.costoPorKm = costoPorKm;
    }

    /** Minutos necesarios para recorrer {@code km} kilómetros a la velocidad del tipo. */
    public int minutosPara(double km) {
        return (int) Math.ceil(km / velocidadKmH * 60.0);
    }

    /** Resuelve el tipo a partir del prefijo del código de unidad (TA01, TM07, TB11). */
    public static TipoVehiculo desdeCodigo(String codigo) {
        String p = codigo.substring(0, 2).toUpperCase();
        for (TipoVehiculo t : values()) {
            if (t.prefijo.equals(p)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Código de unidad desconocido: " + codigo);
    }
}
