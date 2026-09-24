package pe.pucp.paqrap.modelo;

/**
 * Incidencia de avería de una unidad de transporte (LE072, LE099).
 *
 * <p>
 * El enunciado distingue tres tipos de avería con tiempos de inoperatividad
 * crecientes. Los valores por defecto son parámetros configurables del
 * simulador; el planificador solo necesita saber hasta qué minuto la unidad no
 * admite asignaciones (LE076, LE087).
 * </p>
 */
public class Averia {

    /** Tipos de avería con su tiempo de inoperatividad por defecto, en minutos. */
    public enum Tipo {
        TIPO_1(120), // incidente leve, la unidad se recupera en el mismo turno
        TIPO_2(360), // requiere traslado a taller
        TIPO_3(1440); // inmoviliza la unidad por el resto del día simulado

        private int minutosInoperatividad;

        Tipo(int minutosInoperatividad) {
            this.minutosInoperatividad = minutosInoperatividad;
        }

        public int getMinutosInoperatividad() {
            return minutosInoperatividad;
        }

        public void setMinutosInoperatividad(int minutos) {
            this.minutosInoperatividad = minutos;
        }
    }

    private final String codigoUnidad;
    private final Tipo tipo;
    private final int minutoOcurrencia;
    private final Coordenada ubicacion;

    public Averia(String codigoUnidad, Tipo tipo, int minutoOcurrencia, Coordenada ubicacion) {
        this.codigoUnidad = codigoUnidad;
        this.tipo = tipo;
        this.minutoOcurrencia = minutoOcurrencia;
        this.ubicacion = ubicacion;
    }

    public String getCodigoUnidad() {
        return codigoUnidad;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public int getMinutoOcurrencia() {
        return minutoOcurrencia;
    }

    public Coordenada getUbicacion() {
        return ubicacion;
    }

    /**
     * Minuto en que la unidad vuelve automáticamente al estado disponible (LE076).
     */
    public int minutoRecuperacion() {
        return minutoOcurrencia + tipo.getMinutosInoperatividad();
    }

    public boolean vigenteEn(int minuto) {
        return minuto >= minutoOcurrencia && minuto < minutoRecuperacion();
    }

    @Override
    public String toString() {
        return "Averia[" + codigoUnidad + " " + tipo + " @" + Turnos.formatear(minutoOcurrencia) + "]";
    }
}
