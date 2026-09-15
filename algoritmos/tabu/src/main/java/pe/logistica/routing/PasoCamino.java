package pe.logistica.routing;

import pe.logistica.model.Nodo;
import java.time.LocalDateTime;

/** La espera sucede en origen antes de salida; el cruce nunca usa una calle bloqueada. */
public record PasoCamino(Nodo origen, Nodo destino, LocalDateTime salida, LocalDateTime llegada) { }
