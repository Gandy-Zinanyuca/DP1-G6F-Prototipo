package pe.logistica.model;

import java.time.LocalDateTime;

public record Visita(Entrega entrega, LocalDateTime llegada, LocalDateTime inicioAtencion,
                     LocalDateTime finAtencion, boolean plazoIncluyeServicio) {
    public Visita(Entrega entrega, LocalDateTime llegada, LocalDateTime inicio, LocalDateTime fin) {
        this(entrega, llegada, inicio, fin, true);
    }
    public Pedido pedido() { return entrega.pedido(); }
    public boolean dentroDelPlazo() {
        return !(plazoIncluyeServicio ? finAtencion : llegada).isAfter(pedido().deadline());
    }
}
