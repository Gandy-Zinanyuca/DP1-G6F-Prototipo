package pe.logistica.model;

import pe.logistica.routing.Camino;
import java.time.LocalDateTime;
import java.util.List;

public record ResultadoRuta(Ruta ruta, LocalDateTime salida, LocalDateTime fin,
                            List<Visita> visitas, List<Camino> caminos,
                            double distanciaKm, double tiempoHoras, double costo,
                            List<String> incumplimientos) {
    public ResultadoRuta {
        visitas = List.copyOf(visitas); caminos = List.copyOf(caminos);
        incumplimientos = List.copyOf(incumplimientos);
    }
    public boolean factible() { return incumplimientos.isEmpty(); }
}
