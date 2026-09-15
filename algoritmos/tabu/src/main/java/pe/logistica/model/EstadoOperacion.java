package pe.logistica.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record EstadoOperacion(LocalDateTime instantePlanificacion, List<Pedido> pedidos,
                              List<Vehiculo> vehiculos, List<Bloqueo> bloqueos,
                              List<Mantenimiento> mantenimientos, ParametrosOperacion parametros) {
    public EstadoOperacion(LocalDateTime t, List<Pedido> pedidos, List<Vehiculo> vehiculos,
                           List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos) {
        this(t, pedidos, vehiculos, bloqueos, mantenimientos, ParametrosOperacion.legado());
    }
    public EstadoOperacion {
        Objects.requireNonNull(parametros);
        Objects.requireNonNull(instantePlanificacion);
        pedidos = List.copyOf(pedidos); vehiculos = List.copyOf(vehiculos);
        bloqueos = List.copyOf(bloqueos); mantenimientos = List.copyOf(mantenimientos);
        var ids = new HashSet<String>();
        for (Pedido p : pedidos) if (!ids.add(p.id())) throw new IllegalArgumentException("Pedido duplicado: " + p.id());
        ids.clear();
        for (Vehiculo v : vehiculos) if (!ids.add(v.codigo())) throw new IllegalArgumentException("Vehiculo duplicado: " + v.codigo());
        for (Mantenimiento m : mantenimientos) if (!ids.contains(m.codigoVehiculo()))
            throw new IllegalArgumentException("Mantenimiento de vehiculo desconocido: " + m.codigoVehiculo());
    }
}
