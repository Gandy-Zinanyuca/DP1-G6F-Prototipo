package pe.logistica.model;

import java.util.Objects;

/** Fraccion de un pedido entregada por un unico vehiculo dentro de una ruta. */
public record Entrega(Pedido pedido, int cantidad) {
    public Entrega {
        Objects.requireNonNull(pedido);
        if (cantidad <= 0 || cantidad > pedido.cantidad())
            throw new IllegalArgumentException("Entrega: cantidad debe ser positiva y no exceder el pedido");
    }
}
