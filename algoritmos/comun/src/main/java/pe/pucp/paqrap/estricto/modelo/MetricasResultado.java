package pe.pucp.paqrap.estricto.modelo;
public record MetricasResultado(double taMs, double objetivo, double costoOperacion, double distanciaKm,
 double tiempoRutasMinutos, double cumplimientoPedidos, double cumplimientoPaquetes,
 int vehiculosUsados, double utilizacionCapacidad, int iteraciones, long candidatosEvaluados,
 long insercionesIniciales, int pedidosTotales, int pedidosCompletos, int paquetesPendientes,
 String parada) {}
