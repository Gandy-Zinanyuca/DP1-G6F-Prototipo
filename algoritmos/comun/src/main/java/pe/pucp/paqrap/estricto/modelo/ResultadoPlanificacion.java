package pe.pucp.paqrap.estricto.modelo;

public record ResultadoPlanificacion(String algoritmo, Solucion solucion, EvaluacionSolucion evaluacion,
        MetricasResultado metricas) {
}
