package pe.logistica.model;

import java.util.List;
import java.util.Objects;

public record Ruta(Vehiculo vehiculo, List<Pedido> pedidos) {
    public Ruta { Objects.requireNonNull(vehiculo); pedidos = List.copyOf(pedidos); }
    public int carga() { return Math.toIntExact(pedidos.stream().mapToLong(Pedido::cantidad).sum()); }
}
