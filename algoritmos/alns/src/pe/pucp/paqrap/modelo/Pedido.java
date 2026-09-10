package pe.pucp.paqrap.modelo;

/**
 * Pedido de producto P registrado por un cliente.
 *
 * <p>El tiempo se maneja como minutos enteros transcurridos desde el instante 0 de la
 * simulación (día 01, 00:00 del mes cargado). La hora límite se deriva del plazo elegido
 * (36 h regular; 4, 8, 12 o 18 h priorizado) según LE002, y el incumplimiento de una sola
 * hora límite constituye colapso logístico (LE021).</p>
 */
public class Pedido {

    /** Estados del ciclo de vida del pedido (LE013, LE039). */
    public enum Estado {
        REGISTRADO,
        ASIGNADO,
        EN_RUTA,
        ENTREGADO,
        REASIGNADO,
        NO_CUMPLIDO
    }

    private final int id;
    private final String codigoCliente;
    private final Coordenada destino;
    private final int cantidad;
    private final int minutoRegistro;
    private final int plazoHoras;
    private final int minutoLimite;

    private Estado estado = Estado.REGISTRADO;
    private String unidadAsignada;
    private int minutoEntregaEstimado = -1;
    private int minutoEntregaReal = -1;

    public Pedido(int id, String codigoCliente, Coordenada destino, int cantidad,
                  int minutoRegistro, int plazoHoras) {
        this.id = id;
        this.codigoCliente = codigoCliente;
        this.destino = destino;
        this.cantidad = cantidad;
        this.minutoRegistro = minutoRegistro;
        this.plazoHoras = plazoHoras;
        this.minutoLimite = minutoRegistro + plazoHoras * 60;
    }

    public int getId() {
        return id;
    }

    public String getCodigoCliente() {
        return codigoCliente;
    }

    public Coordenada getDestino() {
        return destino;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getMinutoRegistro() {
        return minutoRegistro;
    }

    public int getPlazoHoras() {
        return plazoHoras;
    }

    public int getMinutoLimite() {
        return minutoLimite;
    }

    /** Un pedido es priorizado cuando su plazo es menor al plazo regular de 36 horas. */
    public boolean esPriorizado() {
        return plazoHoras < 36;
    }

    /**
     * Criticidad del pedido en el instante indicado (LE097): fracción del plazo ya consumida.
     * Valores cercanos a 1 (o mayores) identifican los pedidos que deben atenderse primero
     * en la replanificación por incidencias (LE096).
     */
    public double criticidad(int minutoActual) {
        double transcurrido = minutoActual - minutoRegistro;
        return transcurrido / (double) (plazoHoras * 60);
    }

    /** Minutos restantes hasta el vencimiento del plazo comprometido (LE093). */
    public int holgura(int minutoActual) {
        return minutoLimite - minutoActual;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public String getUnidadAsignada() {
        return unidadAsignada;
    }

    public void setUnidadAsignada(String unidadAsignada) {
        this.unidadAsignada = unidadAsignada;
    }

    public int getMinutoEntregaEstimado() {
        return minutoEntregaEstimado;
    }

    public void setMinutoEntregaEstimado(int minutoEntregaEstimado) {
        this.minutoEntregaEstimado = minutoEntregaEstimado;
    }

    public int getMinutoEntregaReal() {
        return minutoEntregaReal;
    }

    public void setMinutoEntregaReal(int minutoEntregaReal) {
        this.minutoEntregaReal = minutoEntregaReal;
    }

    @Override
    public String toString() {
        return "P" + id + "[" + codigoCliente + " " + destino + " q=" + cantidad
                + " lim=" + Turnos.formatear(minutoLimite) + "]";
    }
}
