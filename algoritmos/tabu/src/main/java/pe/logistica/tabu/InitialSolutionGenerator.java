package pe.logistica.tabu;

import pe.logistica.model.*;
import java.util.*;

/** Insercion determinista por deadline. No es una metaheuristica. */
public final class InitialSolutionGenerator {
    public record ResultadoInicial(Solucion solucion, long insercionesEvaluadas) { }
    private record InsercionParcial(Solucion solucion, long evaluadas) { }

    public ResultadoInicial generar(List<Vehiculo> flota, List<Pedido> pedidos, SolutionEvaluator evaluador) {
        Solucion actual = new Solucion(flota.stream().map(v -> new Ruta(v, List.of())).toList());
        var ordenados = new ArrayList<>(pedidos);
        ordenados.sort(Comparator.comparing(Pedido::deadline).thenComparing(Pedido::id));
        long evaluadas = 0;
        for (Pedido pedido : ordenados) {
            InsercionParcial completa = mejorInsercion(actual, null, pedido, pedido.cantidad(), evaluador);
            evaluadas += completa.evaluadas();
            if (completa.solucion() != null) { actual = completa.solucion(); continue; }
            // No cabe entero en ningun vehiculo: se intenta repartir entre varios antes de rendirse.
            InsercionParcial fraccionada = fraccionar(actual, pedido, evaluador);
            evaluadas += fraccionada.evaluadas();
            // Se devuelve lo construido para informar pedidos asignados y faltantes.
            if (fraccionada.solucion() == null) return new ResultadoInicial(actual, evaluadas);
            actual = fraccionada.solucion();
        }
        return new ResultadoInicial(actual, evaluadas);
    }

    /** Mejor insercion de `cantidad` unidades del pedido; si `soloRuta` no es null, se restringe a esa ruta. */
    private InsercionParcial mejorInsercion(Solucion actual, Integer soloRuta, Pedido pedido, int cantidad, SolutionEvaluator evaluador) {
        Solucion mejor = null; double costo = Double.POSITIVE_INFINITY; long evaluadas = 0;
        for (int i = 0; i < actual.rutas().size(); i++) {
            if (soloRuta != null && i != soloRuta) continue;
            Ruta ruta = actual.rutas().get(i);
            // Pseudocodigo 5.1: solo vehiculos disponibles y con capacidad libre suficiente.
            if (!ruta.vehiculo().disponible()) continue;
            if (ruta.carga() + cantidad > ruta.vehiculo().tipo().capacidad()) continue;
            for (int pos = 0; pos <= ruta.entregas().size(); pos++) {
                var lista = new ArrayList<>(ruta.entregas()); lista.add(pos, new Entrega(pedido, cantidad));
                var rutas = new ArrayList<>(actual.rutas()); rutas.set(i, new Ruta(ruta.vehiculo(), lista));
                Solucion candidato = new Solucion(rutas);
                EvaluacionSolucion ev = evaluador.evaluar(candidato, false); evaluadas++;
                if (ev.factible() && ev.costoTotal() < costo) { mejor = candidato; costo = ev.costoTotal(); }
            }
        }
        return new InsercionParcial(mejor, evaluadas);
    }

    /** Reparte el pedido entre varias rutas, en orden, cuando ninguna sola alcanza para el total. */
    private InsercionParcial fraccionar(Solucion actual, Pedido pedido, SolutionEvaluator evaluador) {
        Solucion parcial = actual; int restante = pedido.cantidad(); long evaluadas = 0;
        for (int i = 0; i < parcial.rutas().size() && restante > 0; i++) {
            Ruta ruta = parcial.rutas().get(i);
            if (!ruta.vehiculo().disponible()) continue;
            int libre = ruta.vehiculo().tipo().capacidad() - ruta.carga();
            if (libre <= 0) continue;
            int cantidad = Math.min(restante, libre);
            InsercionParcial fragmento = mejorInsercion(parcial, i, pedido, cantidad, evaluador);
            evaluadas += fragmento.evaluadas();
            if (fragmento.solucion() != null) { parcial = fragmento.solucion(); restante -= cantidad; }
        }
        return restante > 0 ? new InsercionParcial(null, evaluadas) : new InsercionParcial(parcial, evaluadas);
    }
}
