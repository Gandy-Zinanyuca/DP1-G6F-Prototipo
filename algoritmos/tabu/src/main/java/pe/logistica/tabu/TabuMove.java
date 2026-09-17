package pe.logistica.tabu;

/** Atributos del movimiento independientes de las posiciones de insercion. */
public record TabuMove(Tipo tipo, String pedidoA, String pedidoB, String origen, String destino) {
    public enum Tipo { ASIGNACION, SWAP }
    public record Clave(Tipo tipo, String pedidoA, String pedidoB, String vehiculo) { }
    public static TabuMove asignacion(String pedido, String origen, String destino) {
        return new TabuMove(Tipo.ASIGNACION, pedido, "", origen, destino);
    }
    public static TabuMove swap(String vehiculo, String a, String b) {
        return a.compareTo(b) < 0 ? new TabuMove(Tipo.SWAP, a, b, vehiculo, vehiculo)
                : new TabuMove(Tipo.SWAP, b, a, vehiculo, vehiculo);
    }
    public Clave claveConsulta() { return new Clave(tipo, pedidoA, pedidoB, destino); }
    public Clave claveInversa() { return new Clave(tipo, pedidoA, pedidoB, origen); }
}
