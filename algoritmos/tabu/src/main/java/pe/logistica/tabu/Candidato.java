package pe.logistica.tabu;

import pe.logistica.model.Solucion;

public record Candidato(Solucion solucion, TabuMove movimiento) { }
