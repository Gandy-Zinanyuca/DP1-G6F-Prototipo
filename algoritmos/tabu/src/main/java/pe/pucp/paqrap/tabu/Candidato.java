package pe.pucp.paqrap.tabu;

import pe.pucp.paqrap.estricto.modelo.Solucion;

public record Candidato(Solucion solucion, TabuMove movimiento) {
}
