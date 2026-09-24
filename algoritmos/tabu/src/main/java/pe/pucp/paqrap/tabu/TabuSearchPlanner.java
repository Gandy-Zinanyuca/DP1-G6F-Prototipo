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
        Double primeraMs = evaluacion.completa() && !estado.pedidos().isEmpty() ? (System.nanoTime() - inicio) / 1e6
                : null;
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
                if (tiempoAgotado(inicio)) {
                    parada = "TIEMPO";
                    break;
                }
                var sacudida = diversificar(actual, ev, random);
                if (sacudida == null) {
                    parada = "SIN_VECINO_ADMISIBLE";
                    break;
                }
                actual = sacudida;
                tabu.limpiar();
                sinMejora = 0;
                continue;
            }
            var elegido = selector.elegido();
            if (primeraMs == null && selector.evaluacion().completa()) {
                primeraMs = (System.nanoTime() - inicio) / 1e6;
                primeraIter = iter;
            }
            actual = elegido.solucion();
            tabu.registrar(elegido.movimiento(), iter, config.tenenciaTabu());
            if (selector.evaluacion().objetivo() < mejorCosto - 1e-9) {
                mejor = actual;
                mejorCosto = selector.evaluacion().objetivo();
                sinMejora = 0;
                iteracionMejor = iter;
            } else
                sinMejora++;
            if (sinMejora >= config.sinMejoraMax()) {
                var sacudida = diversificar(actual, ev, random);
                if (sacudida == null) {
                    parada = "ESTANCAMIENTO";
                    break;
                }
                actual = sacudida;
                tabu.limpiar();
                sinMejora = 0;
            }
        }
        return Resultados.crear("TS-estricto", mejor, ev, inicio, iter, iteracionMejor, candidatos, iniciales, parada,
                primeraMs, primeraIter);
    }

    /**
     * Sacude la solucion actual reinsertando al azar una fraccion de sus partes.
     * Permite abandonar un optimo local en vez de terminar la busqueda; el mejor
     * global se conserva aparte. Devuelve null si no hay nada movible.
     */
    private Solucion diversificar(Solucion s, EvaluadorFactibilidad ev, Random random) {
        var movibles = new ArrayList<PartePedido>();
        for (var ruta : s.rutas())
            if (!ruta.enCurso())
                movibles.addAll(ruta.partes());
        if (movibles.isEmpty())
            return null;
        Collections.shuffle(movibles, random);
        var quitar = new HashSet<>(movibles.subList(0, Math.min(movibles.size(), Math.max(2, movibles.size() / 8))));
        var rutas = new ArrayList<Ruta>();
        var pendientes = new ArrayList<>(s.pendientes());
        for (var ruta : s.rutas()) {
            var lista = new ArrayList<PartePedido>();
            for (var parte : ruta.partes())
                if (quitar.contains(parte))
                    pendientes.add(parte);
                else
                    lista.add(parte);
            rutas.add(ruta.conPartes(lista));
        }
        return GeneradorSolucionInicial.reparar(new Solucion(rutas, pendientes), ev, true, random);
    }

    private boolean tiempoAgotado(long inicio) {
        return config.presupuestoMs() > 0 && (System.nanoTime() - inicio) / 1_000_000 >= config.presupuestoMs();
    }
}
