package pe.pucp.paqrap.estricto.modelo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Intervalo temporal semiabierto [inicio, fin). */
public record Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Nodo> puntos) {
    public Bloqueo {
        Objects.requireNonNull(inicio); Objects.requireNonNull(fin); puntos = List.copyOf(puntos);
        if (!inicio.isBefore(fin) || puntos.size() < 2)
            throw new IllegalArgumentException("Bloqueo: intervalo positivo y al menos dos puntos");
        for (int i = 1; i < puntos.size(); i++) {
            Nodo a = puntos.get(i - 1), b = puntos.get(i);
            if (a.equals(b) || (a.x() != b.x() && a.y() != b.y()))
                throw new IllegalArgumentException("Tramo debe ser horizontal o vertical y no vacio: " + a + " -> " + b);
        }
    }
}
