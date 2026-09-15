package pe.logistica.metrics;

public record RendimientoAlgoritmo(double taMs, int iteraciones, long candidatosEvaluados,
                                  long solucionesFactiblesEvaluadas, long movimientosTabuRechazados,
                                  long aspiracionesAplicadas, int iteracionMejorSolucion,
                                  long insercionesInicialesEvaluadas, double costoSolucionInicial,
                                  String motivoParada) { }
