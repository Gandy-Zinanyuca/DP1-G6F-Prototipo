package pe.logistica.model;

import java.util.List;

/** Valor inmutable: los vecinos nunca modifican la solucion actual o la mejor. */
public record Solucion(List<Ruta> rutas) {
    public Solucion { rutas = List.copyOf(rutas); }
}
