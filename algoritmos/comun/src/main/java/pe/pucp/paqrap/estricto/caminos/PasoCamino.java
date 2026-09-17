package pe.pucp.paqrap.estricto.caminos;

import pe.pucp.paqrap.estricto.modelo.Nodo;
import java.time.LocalDateTime;

/** La espera sucede en origen antes de salida; el cruce nunca usa una calle bloqueada. */
public record PasoCamino(Nodo origen, Nodo destino, LocalDateTime salida, LocalDateTime llegada) { }
