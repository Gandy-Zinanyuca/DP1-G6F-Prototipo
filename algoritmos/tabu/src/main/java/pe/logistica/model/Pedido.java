package pe.logistica.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record Pedido(String id, LocalDateTime fechaRegistro, Nodo ubicacion, int cantidad, int plazoHoras, String clienteId) {
    public Pedido(String id, LocalDateTime fechaRegistro, Nodo ubicacion, int cantidad, int plazoHoras) {
        this(id, fechaRegistro, ubicacion, cantidad, plazoHoras, id);
    }
    public Pedido {
        Objects.requireNonNull(clienteId);
        if (clienteId.isBlank()) throw new IllegalArgumentException("Cliente no puede estar vacio");
        Objects.requireNonNull(id); Objects.requireNonNull(fechaRegistro); Objects.requireNonNull(ubicacion);
        if (id.isBlank() || cantidad <= 0 || plazoHoras <= 0)
            throw new IllegalArgumentException("Pedido: id no vacio, cantidad y plazo positivos");
    }
    public LocalDateTime deadline() { return fechaRegistro.plusHours(plazoHoras); }
}
