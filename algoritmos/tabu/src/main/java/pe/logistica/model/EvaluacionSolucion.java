package pe.logistica.model;

import java.util.List;

public record EvaluacionSolucion(List<ResultadoRuta> rutas, List<String> incumplimientos) {
    public EvaluacionSolucion { rutas = List.copyOf(rutas); incumplimientos = List.copyOf(incumplimientos); }
    public boolean factible() { return incumplimientos.isEmpty(); }
    public double costoTotal() { return rutas.stream().mapToDouble(ResultadoRuta::costo).sum(); }
    public double distanciaTotalKm() { return rutas.stream().mapToDouble(ResultadoRuta::distanciaKm).sum(); }
    public double tiempoTotalHoras() { return rutas.stream().mapToDouble(ResultadoRuta::tiempoHoras).sum(); }
}
