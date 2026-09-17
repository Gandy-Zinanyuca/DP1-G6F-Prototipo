package pe.logistica.model;

import java.util.List;
import java.util.Objects;

public record Ruta(Vehiculo vehiculo, List<Entrega> entregas) {
    public Ruta { Objects.requireNonNull(vehiculo); entregas = List.copyOf(entregas); }
    public int carga() { return Math.toIntExact(entregas.stream().mapToLong(Entrega::cantidad).sum()); }
}
