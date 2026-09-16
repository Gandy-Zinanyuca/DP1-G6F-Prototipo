package pe.logistica.model;

import pe.logistica.metrics.MetricasResultado;

/** Incluye la evaluacion y trazas para auditar la factibilidad de mejorSolucion. */
public record ResultadoPlanificacion(Solucion mejorSolucion, MetricasResultado metricas,
                                    EvaluacionSolucion evaluacion, ParametrosOperacion parametros) {
    public ResultadoPlanificacion(Solucion solucion, MetricasResultado metricas, EvaluacionSolucion evaluacion) {
        this(solucion, metricas, evaluacion, ParametrosOperacion.legado());
    }
}
