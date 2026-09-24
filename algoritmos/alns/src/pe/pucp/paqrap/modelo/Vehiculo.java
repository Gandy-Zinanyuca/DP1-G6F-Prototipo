package pe.pucp.paqrap.modelo;

/**
 * Unidad de transporte de la flota.
 *
 * <p>
 * La instancia representa el estado de la unidad en el instante en que arranca
 * un ciclo de planificación: dónde está, desde cuándo queda libre y si ya
 * consumió su hora de alimentación en el turno vigente. El planificador nunca
 * modifica este estado; construye rutas sobre él y devuelve la asignación al
 * simulador, que es quien avanza el reloj.
 * </p>
 */
public class Vehiculo {

    /** Estados operativos de la unidad (LE042, LE076, LE087, LE089). */
    public enum Estado {
        DISPONIBLE, EN_RUTA, AVERIADO, EN_MANTENIMIENTO, EN_ALIMENTACION
    }

    private final String codigo;
    private final TipoVehiculo tipo;

    private Coordenada posicion;
    private Estado estado = Estado.DISPONIBLE;
    private int minutoDisponibleDesde;
    private int cargaActual;
    private int minutoInicioAlimentacion = -1;
    private int turnoDeUltimaAlimentacion = -1;

    public Vehiculo(String codigo, TipoVehiculo tipo, Coordenada posicionInicial) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.posicion = posicionInicial;
    }

    public String getCodigo() {
        return codigo;
    }

    public TipoVehiculo getTipo() {
        return tipo;
    }

    public int getCapacidad() {
        return tipo.getCapacidad();
    }

    public Coordenada getPosicion() {
        return posicion;
    }

    public void setPosicion(Coordenada posicion) {
        this.posicion = posicion;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public int getMinutoDisponibleDesde() {
        return minutoDisponibleDesde;
    }

    public void setMinutoDisponibleDesde(int minutoDisponibleDesde) {
        this.minutoDisponibleDesde = minutoDisponibleDesde;
    }

    public int getCargaActual() {
        return cargaActual;
    }

    public void setCargaActual(int cargaActual) {
        this.cargaActual = cargaActual;
    }

    public int getMinutoInicioAlimentacion() {
        return minutoInicioAlimentacion;
    }

    public void setMinutoInicioAlimentacion(int minutoInicioAlimentacion) {
        this.minutoInicioAlimentacion = minutoInicioAlimentacion;
    }

    public int getTurnoDeUltimaAlimentacion() {
        return turnoDeUltimaAlimentacion;
    }

    public void setTurnoDeUltimaAlimentacion(int turnoDeUltimaAlimentacion) {
        this.turnoDeUltimaAlimentacion = turnoDeUltimaAlimentacion;
    }

    /**
     * Indica si la unidad admite nuevas asignaciones en el ciclo actual (LE087,
     * LE089): solo las unidades disponibles o ya en ruta pueden recibir pedidos.
     */
    public boolean asignable() {
        return estado == Estado.DISPONIBLE || estado == Estado.EN_RUTA;
    }

    /**
     * Clave de identidad de la unidad en la solución (una ruta por unidad y ciclo).
     */
    @Override
    public String toString() {
        return codigo + "[" + tipo + " cap=" + getCapacidad() + " @" + posicion + "]";
    }
}
