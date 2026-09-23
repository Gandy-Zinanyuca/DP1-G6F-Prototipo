package pe.pucp.paqrap.modelo;

/**
 * Mantenimiento preventivo programado de una unidad.
 *
 * <p>Formato del archivo {@code mant.preventivo}: {@code AAAAMMDD:TXNN}, una línea por
 * unidad y fecha. La unidad queda fuera de servicio durante todo el día indicado, por lo que
 * el planificador la excluye del conjunto de unidades asignables de ese día.</p>
 */
public class Mantenimiento {

    private final int anio;
    private final int mes;
    private final int diaDelMes;
    private final String codigoUnidad;

    public Mantenimiento(int anio, int mes, int diaDelMes, String codigoUnidad) {
        this.anio = anio;
        this.mes = mes;
        this.diaDelMes = diaDelMes;
        this.codigoUnidad = codigoUnidad;
    }

    public int getAnio() {
        return anio;
    }

    public int getMes() {
        return mes;
    }

    public int getDiaDelMes() {
        return diaDelMes;
    }

    public java.time.LocalDate getFecha() {
        return java.time.LocalDate.of(anio, mes, diaDelMes);
    }

    public String getCodigoUnidad() {
        return codigoUnidad;
    }

    /** Indica si el mantenimiento afecta al día simulado indicado del mes cargado. */
    public boolean afectaDia(int anioSimulado, int mesSimulado, int dia) {
        return anio == anioSimulado && mes == mesSimulado && diaDelMes == dia;
    }

    @Override
    public String toString() {
        return String.format("Mant[%04d-%02d-%02d %s]", anio, mes, diaDelMes, codigoUnidad);
    }
}
