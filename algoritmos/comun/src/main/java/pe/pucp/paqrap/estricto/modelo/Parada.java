package pe.pucp.paqrap.estricto.modelo;

import java.time.LocalDateTime;
import java.util.List;

/** Partes consecutivas de un mismo pedido comparten un solo servicio. */
public record Parada(List<PartePedido> partes, LocalDateTime llegada, LocalDateTime finServicio) {
    public Parada {
        partes = List.copyOf(partes);
    }
}
