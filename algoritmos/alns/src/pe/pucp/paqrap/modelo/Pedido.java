package pe.pucp.paqrap.modelo;

/**
 * Pedido de producto P registrado por un cliente.
 *
 * <p>
 * El tiempo se maneja como minutos enteros transcurridos desde el instante 0 de
 * la simulación (día 01, 00:00 del mes cargado). La hora límite se deriva del
 * plazo elegido (36 h regular; 4, 8, 12 o 18 h priorizado) según LE002, y el
 * incumplimiento de una sola hora límite constituye colapso logístico (LE021).
 * </p>
 *
 * <h2>Fraccionamiento</h2>
 * <p>
 * Un pedido puede repartirse entre varias unidades. Cada parte es una
 * <i>fracción</i>: un objeto {@code Pedido} con el mismo id, destino, registro
 * y hora límite que el pedido original, pero con una cantidad menor. Las rutas
 * contienen fracciones o pedidos completos indistintamente;
 * {@link #getOriginal()} devuelve siempre el pedido registrado. La identidad de
 * cada parte es la del objeto (no se redefine {@code equals}).
 * </p>
 *
 * <p>
 * El pedido original lleva, además, la cuenta de las unidades ya despachadas
 * (cargadas en una unidad que salió del almacén) y entregadas; con ellas el
 * simulador sabe qué cantidad queda por planificar y cuándo el pedido está
 * completamente entregado.
 * </p>
 */
public class Pedido {

    /** Estados del ciclo de vida del pedido (LE013, LE039). */
    public enum Estado {
        REGISTRADO, ASIGNADO, EN_RUTA, ENTREGADO, REASIGNADO, NO_CUMPLIDO
    }

    private final int id;
    private final String codigoCliente;
    private final Coordenada destino;
    private final int cantidad;
    private final int minutoRegistro;
    private final int plazoHoras;
    private final int minutoLimite;
    /**
     * Pedido registrado del que esta parte es fracción; {@code null} si es el
     * original.
     */
    private final Pedido original;

    private int cantidadDespachada;
    private int cantidadEntregada;
    private int partesDespachadas;

    private Estado estado = Estado.REGISTRADO;
    private String unidadAsignada;
    private int minutoEntregaEstimado = -1;
    private int minutoEntregaReal = -1;

    public Pedido(int id, String codigoCliente, Coordenada destino, int cantidad, int minutoRegistro, int plazoHoras) {
        this.id = id;
        this.codigoCliente = codigoCliente;
        this.destino = destino;
        this.cantidad = cantidad;
        this.minutoRegistro = minutoRegistro;
        this.plazoHoras = plazoHoras;
        this.minutoLimite = minutoRegistro + plazoHoras * 60;
        this.original = null;
    }

    private Pedido(Pedido original, int cantidad) {
        this.id = original.id;
        this.codigoCliente = original.codigoCliente;
        this.destino = original.destino;
        this.cantidad = cantidad;
        this.minutoRegistro = original.minutoRegistro;
        this.plazoHoras = original.plazoHoras;
        this.minutoLimite = original.minutoLimite;
        this.original = original;
    }

    /**
     * Crea una fracción de {@code cantidad} unidades del pedido original. La
     * fracción de una fracción se refiere siempre al pedido registrado.
     */
    public Pedido fraccion(int cantidad) {
        Pedido raiz = getOriginal();
        if (cantidad <= 0 || cantidad > raiz.cantidad) {
            throw new IllegalArgumentException("fracción inválida de P" + id + ": " + cantidad);
        }
        return new Pedido(raiz, cantidad);
    }

    /**
     * Pedido registrado al que pertenece esta parte (él mismo si no es fracción).
     */
    public Pedido getOriginal() {
        return original == null ? this : original;
    }

    public boolean esFraccion() {
        return original != null;
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

    /**
     * Un pedido es priorizado cuando su plazo es menor al plazo regular de 36
     * horas.
     */
    public boolean esPriorizado() {
        return plazoHoras < 36;
    }

    /**
     * Criticidad del pedido en el instante indicado (LE097): fracción del plazo ya
     * consumida. Valores cercanos a 1 (o mayores) identifican los pedidos que deben
     * atenderse primero en la replanificación por incidencias (LE096).
     */
    public double criticidad(int minutoActual) {
        double transcurrido = minutoActual - minutoRegistro;
        return transcurrido / (double) (plazoHoras * 60);
    }

    /** Minutos restantes hasta el vencimiento del plazo comprometido (LE093). */
    public int holgura(int minutoActual) {
        return minutoLimite - minutoActual;
    }

    // ------------------------------------------------------------------ avance de
    // la simulación

    /**
     * Unidades del pedido original ya cargadas en unidades que salieron del
     * almacén.
     */
    public int getCantidadDespachada() {
        return cantidadDespachada;
    }

    /**
     * Unidades del pedido original que aún no se despachan: lo que queda por
     * planificar.
     */
    public int cantidadPendiente() {
        return cantidad - cantidadDespachada;
    }

    /**
     * Número de partes en que se despachó el pedido (más de una si se fraccionó).
     */
    public int getPartesDespachadas() {
        return partesDespachadas;
    }

    public int getCantidadEntregada() {
        return cantidadEntregada;
    }

    /** Registra en el pedido original el despacho de una de sus partes. */
    public void registrarDespacho(int unidades) {
        Pedido raiz = getOriginal();
        raiz.cantidadDespachada += unidades;
        raiz.partesDespachadas++;
        if (raiz.cantidadDespachada >= raiz.cantidad && raiz.estado != Estado.ENTREGADO) {
            raiz.estado = Estado.EN_RUTA;
        }
    }

    /**
     * Registra en el pedido original la entrega de una de sus partes en el minuto
     * indicado.
     *
     * @return verdadero si con esta entrega el pedido quedó completamente entregado
     */
    public boolean registrarEntrega(int unidades, int minuto) {
        Pedido raiz = getOriginal();
        raiz.cantidadEntregada += unidades;
        raiz.minutoEntregaReal = Math.max(raiz.minutoEntregaReal, minuto);
        if (raiz.cantidadEntregada >= raiz.cantidad) {
            raiz.estado = Estado.ENTREGADO;
            return true;
        }
        return false;
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
        String q = esFraccion() ? cantidad + "/" + original.cantidad : String.valueOf(cantidad);
        return "P" + id + "[" + codigoCliente + " " + destino + " q=" + q + " lim=" + Turnos.formatear(minutoLimite)
                + "]";
    }
}
