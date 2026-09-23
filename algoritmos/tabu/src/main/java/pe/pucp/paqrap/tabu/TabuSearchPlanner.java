package pe.pucp.paqrap.tabu;

import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * TS con movimientos inversos tabu, aspiracion y alternancia asignacion/ruteo.
 */
public final class TabuSearchPlanner implements PlanificadorEstricto {
    private final ConfiguracionTabu config;

    public TabuSearchPlanner() {
        this(ConfiguracionTabu.porDefecto());
    }

    public TabuSearchPlanner(ConfiguracionTabu c) {
        config = Objects.requireNonNull(c);
    }

    public ResultadoPlanificacion planificar(EstadoOperacion estado, ParametrosOperacion parametros) {
        long inicio = System.nanoTime();
        var ev = new EvaluadorFactibilidad(estado, parametros);
        Solucion actual = GeneradorSolucionInicial.generar(ev), mejor = actual;
        long iniciales = ev.evaluaciones();
        var evaluacion = ev.evaluar(actual);
        if (!evaluacion.factible())
            throw new IllegalStateException("Inicial invalida");
        double mejorCosto = evaluacion.objetivo();
        Double primeraMs = evaluacion.completa() && !estado.pedidos().isEmpty() ? (System.nanoTime()-inicio)/1e6 : null;
        Integer primeraIter = primeraMs == null ? null : 0;
        var tabu = new TabuList();
        var random = new Random(config.semilla());
        var asignacion = new AssignmentNeighborhood();
        var ruteo = new RoutingNeighborhood();
        int iter = 0, sinMejora = 0, iteracionMejor = 0;
        long candidatos = 0;
        String parada = "MAX_ITERACIONES";
        while (iter < config.maxIteraciones()) {
            if (tiempoAgotado(inicio)) {
                parada = "TIEMPO";
                break;
            }
            if (estado.pedidos().isEmpty()) {
                parada = "SIN_PEDIDOS";
                break;
            }
            iter++;
            tabu.depurar(iter);
            var selector = new CandidateSelector(tabu, iter, mejorCosto);
            // Cada fase tiene presupuesto propio; el orden se alterna.
            for (int fase = 0; fase < 2; fase++) {
                final int[] usados = { 0 };
                final int cupo = config.candidatosPorIteracion() / 2;
                Predicate<Candidato> consumir = c -> {
                    if (usados[0] >= cupo || tiempoAgotado(inicio))
                        return false;
                    usados[0]++;
                    selector.considerar(c, ev.evaluar(c.solucion()));
                    return usados[0] < cupo;
                };
                if ((iter + fase) % 2 == 0)
                    asignacion.generar(actual, estado, random, consumir);
                else
                    ruteo.generar(actual, random, consumir);
                candidatos += usados[0];
            }
            if (selector.elegido() == null) {
                parada = tiempoAgotado(inicio) ? "TIEMPO" : "SIN_VECINO_ADMISIBLE";
                break;
            }
            var elegido = selector.elegido();
            if (primeraMs == null && selector.evaluacion().completa()) {
                primeraMs = (System.nanoTime()-inicio)/1e6;
                primeraIter = iter;
            }
            actual = elegido.solucion();
            tabu.registrar(elegido.movimiento(), iter, config.tenenciaTabu());
            if (selector.evaluacion().objetivo() < mejorCosto - 1e-9) {
                mejor = actual;
                mejorCosto = selector.evaluacion().objetivo();
                sinMejora = 0; iteracionMejor = iter;
            } else
                sinMejora++;
            if (sinMejora >= config.sinMejoraMax()) {
                parada = "ESTANCAMIENTO";
                break;
            }
        }
        return Resultados.crear("TS-estricto", mejor, ev, inicio, iter, iteracionMejor, candidatos, iniciales, parada,
                primeraMs, primeraIter);
    }

    private boolean tiempoAgotado(long inicio) {
        return config.presupuestoMs() > 0 && (System.nanoTime() - inicio) / 1_000_000 >= config.presupuestoMs();
    }
}
