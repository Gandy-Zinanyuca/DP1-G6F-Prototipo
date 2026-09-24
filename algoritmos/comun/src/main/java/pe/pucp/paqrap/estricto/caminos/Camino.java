package pe.pucp.paqrap.estricto.caminos;

import pe.pucp.paqrap.estricto.modelo.Nodo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

public record Camino(Nodo origen, LocalDateTime inicio, LocalDateTime llegada, List<PasoCamino> pasos) {
    public Camino {
        pasos = List.copyOf(pasos);
    }

    public int distanciaKm() {
        return pasos.size();
    }

    public Duration duracion() {
        return Duration.between(inicio, llegada);
    }

    public List<Nodo> coordenadas() {
        var nodos = new ArrayList<Nodo>();
        nodos.add(origen);
        for (PasoCamino p : pasos)
            nodos.add(p.destino());
        return List.copyOf(nodos);
    }
}
