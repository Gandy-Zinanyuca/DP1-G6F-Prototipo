package pe.logistica.tabu;

import pe.logistica.model.*;
import pe.logistica.metrics.*;
import java.util.Objects;
import java.util.function.Consumer;

public class TabuSearchPlanner {
    public ResultadoPlanificacion ejecutar(EstadoOperacion estado, ConfiguracionTabu configuracion) {
        long inicio = System.nanoTime();
        Objects.requireNonNull(estado); Objects.requireNonNull(configuracion);
        var t = estado.instantePlanificacion(); var hasta = t.plusMinutes(configuracion.scMinutos());
        var considerados = estado.pedidos().stream()
                .filter(p -> !p.fechaRegistro().isBefore(t) && !p.fechaRegistro().isAfter(hasta)).toList();
        var evaluador = new SolutionEvaluator(estado, considerados);
        var inicial = new InitialSolutionGenerator().generar(estado.vehiculos(), considerados, evaluador);
        Solucion actual = inicial.solucion(), mejor = actual;
        EvaluacionSolucion mejorEvaluacion = evaluador.evaluar(actual);
        double costoInicial = mejorEvaluacion.costoTotal();
        var tabu = new TabuList();
        var asignacion = new AssignmentNeighborhood(); var ruteo = new RoutingNeighborhood();
        var fraccionamiento = new SplitNeighborhood();
        int iteraciones = 0, iteracionMejor = 0;
        long candidatos = 1, factibles = mejorEvaluacion.factible() ? 1 : 0, rechazados = 0, aspiraciones = 0;
        String parada = "MAXIMO_ITERACIONES";
        if (!mejorEvaluacion.factible()) parada = "SOLUCION_INICIAL_NO_FACTIBLE";
        else if (considerados.isEmpty()) parada = "SIN_PEDIDOS";
        else {
            // Ambos modulos alimentan el mismo selector en cada iteracion.
            while (iteraciones < configuracion.maxIteraciones()) {
                iteraciones++; tabu.depurar(iteraciones);
                var selector = new CandidateSelector(tabu, iteraciones, mejorEvaluacion.costoTotal());
                Consumer<Candidato> evaluar = vecino -> selector.considerar(vecino, evaluador.evaluar(vecino.solucion()));
                asignacion.generar(actual, evaluar);
                ruteo.generar(actual, evaluar);
                fraccionamiento.generar(actual, evaluar);
                candidatos += selector.evaluados(); factibles += selector.factibles();
                rechazados += selector.rechazadosTabu(); aspiraciones += selector.aspiraciones();
                Candidato elegido = selector.elegido();
                if (elegido == null) { parada = "SIN_CANDIDATO_ADMISIBLE"; break; }
                tabu.registrar(elegido.movimiento(), iteraciones, configuracion.tenenciaTabu());
                actual = elegido.solucion(); // Se permiten empeoramientos de costo, nunca de factibilidad.
                if (selector.evaluacionElegida().costoTotal() < mejorEvaluacion.costoTotal()) {
                    mejor = actual; mejorEvaluacion = selector.evaluacionElegida(); iteracionMejor = iteraciones;
                }
            }
        }
        double taMs = (System.nanoTime() - inicio) / 1_000_000.0;
        var rendimiento = new RendimientoAlgoritmo(taMs, iteraciones, candidatos, factibles, rechazados,
                aspiraciones, iteracionMejor, inicial.insercionesEvaluadas(), costoInicial, parada);
        var metricas = MetricasResultado.calcular(estado, configuracion, considerados, mejorEvaluacion, rendimiento);
        return new ResultadoPlanificacion(mejor, metricas, mejorEvaluacion, estado.parametros());
    }
}
