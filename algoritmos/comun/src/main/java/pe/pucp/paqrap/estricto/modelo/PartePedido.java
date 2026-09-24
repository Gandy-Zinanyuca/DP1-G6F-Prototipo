package pe.pucp.paqrap.estricto.modelo;

import java.util.Objects;

/**
 * Cantidad pendiente indivisible durante una busqueda; ID estable entre ciclos.
 */
public record PartePedido(String id, Pedido pedido, int cantidad) {
    public PartePedido {
        Objects.requireNonNull(id);
        Objects.requireNonNull(pedido);
        if (id.isBlank() || cantidad <= 0 || cantidad > pedido.cantidad())
            throw new IllegalArgumentException("Parte invalida");
    }
}
