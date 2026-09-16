package pe.logistica.tabu;

import pe.logistica.model.*;
import java.util.*;

/** Insercion determinista por deadline. No es una metaheuristica. */
public final class InitialSolutionGenerator {
    public record ResultadoInicial(Solucion solucion, long insercionesEvaluadas) { }
    public ResultadoInicial generar(List<Vehiculo> flota, List<Pedido> pedidos, SolutionEvaluator evaluador) {
        Solucion actual = new Solucion(flota.stream().map(v -> new Ruta(v, List.of())).toList());
        var ordenados = new ArrayList<>(pedidos);
        ordenados.sort(Comparator.comparing(Pedido::deadline).thenComparing(Pedido::id));
        long evaluadas = 0;
        for (Pedido pedido : ordenados) {
            Solucion mejor = null; double costo = Double.POSITIVE_INFINITY;
            for (int i = 0; i < actual.rutas().size(); i++) {
                Ruta ruta = actual.rutas().get(i);
                // Pseudocodigo 5.1: solo vehiculos disponibles y con capacidad libre suficiente.
                if (!ruta.vehiculo().disponible()) continue;
                if (ruta.carga() + pedido.cantidad() > ruta.vehiculo().tipo().capacidad()) continue;
                for (int pos = 0; pos <= ruta.pedidos().size(); pos++) {
                    var lista = new ArrayList<>(ruta.pedidos()); lista.add(pos, pedido);
                    var rutas = new ArrayList<>(actual.rutas()); rutas.set(i, new Ruta(ruta.vehiculo(), lista));
                    Solucion candidato = new Solucion(rutas);
                    EvaluacionSolucion ev = evaluador.evaluar(candidato, false); evaluadas++;
                    if (ev.factible() && ev.costoTotal() < costo) { mejor = candidato; costo = ev.costoTotal(); }
                }
            }
            // Se devuelve lo construido para informar pedidos asignados y faltantes.
            if (mejor == null) return new ResultadoInicial(actual, evaluadas);
            actual = mejor;
        }
        return new ResultadoInicial(actual, evaluadas);
    }
}
