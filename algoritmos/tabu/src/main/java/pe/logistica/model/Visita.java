package pe.logistica.model;

import java.time.LocalDateTime;

public record Visita(Pedido pedido, LocalDateTime llegada, LocalDateTime inicioAtencion,
                     LocalDateTime finAtencion, boolean plazoIncluyeServicio) {
    public Visita(Pedido pedido, LocalDateTime llegada, LocalDateTime inicio, LocalDateTime fin) {
        this(pedido, llegada, inicio, fin, true);
    }
    public boolean dentroDelPlazo() {
        return !(plazoIncluyeServicio ? finAtencion : llegada).isAfter(pedido.deadline());
    }
}
