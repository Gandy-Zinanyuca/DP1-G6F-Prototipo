package pe.logistica;

import org.junit.jupiter.api.Test;
import pe.logistica.model.*;
import pe.logistica.tabu.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TabuNeighborhoodTest {
    private final LocalDateTime t = LocalDateTime.of(2026, 9, 11, 13, 30);
    private final Vehiculo a = new Vehiculo("TA01", new Nodo(0, 0)), b = new Vehiculo("TM01", new Nodo(0, 0));
    private Pedido p(String id) { return new Pedido(id, t, new Nodo(5, 0), 1, 10); }
    @Test void asignacionPruebaTodasLasPosicionesSinMutarLaFuente() {
        Pedido p1 = p("P1"), p2 = p("P2"), p3 = p("P3");
        var s = new Solucion(List.of(new Ruta(a, List.of(p1)), new Ruta(b, List.of(p2, p3))));
        List<Candidato> vecinos = new ArrayList<>(); new AssignmentNeighborhood().generar(s, vecinos::add);
        var traslado = vecinos.stream().filter(c -> c.movimiento().pedidoA().equals("P1")).toList();
        assertEquals(3, traslado.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(p1, traslado.get(i).solucion().rutas().get(1).pedidos().get(i));
            assertTrue(traslado.get(i).solucion().rutas().get(0).pedidos().isEmpty());
        }
        assertEquals(List.of(p1), s.rutas().get(0).pedidos());
        assertThrows(UnsupportedOperationException.class, () -> s.rutas().clear());
    }
    @Test void swapCambiaOrdenSinCambiarAsignacion() {
        var pedidos = List.of(p("P1"), p("P2"), p("P3"), p("P4"));
        var s = new Solucion(List.of(new Ruta(a, pedidos), new Ruta(b, List.of())));
        List<Candidato> vecinos = new ArrayList<>(); new RoutingNeighborhood().generar(s, vecinos::add);
        assertEquals(6, vecinos.size());
        assertTrue(vecinos.stream().anyMatch(c -> c.solucion().rutas().get(0).pedidos().equals(List.of(pedidos.get(0), pedidos.get(3), pedidos.get(2), pedidos.get(1)))));
        for (Candidato c : vecinos) {
            assertEquals(new HashSet<>(pedidos), new HashSet<>(c.solucion().rutas().get(0).pedidos()));
            assertTrue(c.solucion().rutas().get(1).pedidos().isEmpty());
        }
    }
    @Test void tenenciaProhibeRetornoAlVehiculoAnteriorYPuedeExpirar() {
        var tabu = new TabuList(); var ida = TabuMove.asignacion("P", "TA01", "TM01");
        var vuelta = TabuMove.asignacion("P", "TM01", "TA01");
        tabu.registrar(ida, 4, 3);
        assertTrue(tabu.esTabu(vuelta, 5)); assertTrue(tabu.esTabu(vuelta, 7));
        assertFalse(tabu.esTabu(TabuMove.asignacion("P", "TM01", "TB01"), 5));
        tabu.depurar(8); assertFalse(tabu.esTabu(vuelta, 8));
    }
    @Test void swapInversoEsTabuAunqueCambieOrdenDeIds() {
        var tabu = new TabuList(); tabu.registrar(TabuMove.swap("TA01", "P1", "P4"), 1, 2);
        assertTrue(tabu.esTabu(TabuMove.swap("TA01", "P4", "P1"), 2));
        assertFalse(tabu.esTabu(TabuMove.swap("TM01", "P4", "P1"), 2));
    }
    @Test void aspiracionSoloAdmiteTabuFactibleQueMejoraEstrictoGlobal() {
        Pedido p = p("P"); var estado = new EstadoOperacion(t, List.of(p), List.of(a, b), List.of(), List.of());
        var evaluador = new SolutionEvaluator(estado, estado.pedidos());
        var candidato = new Candidato(new Solucion(List.of(new Ruta(a, List.of()), new Ruta(b, List.of(p)))), TabuMove.asignacion("P", "TA01", "TM01"));
        var evaluacion = evaluador.evaluar(candidato.solucion()); assertEquals(60, evaluacion.costoTotal());
        var tabu = new TabuList(); tabu.registrar(TabuMove.asignacion("P", "TM01", "TA01"), 1, 7);
        var mejora = new CandidateSelector(tabu, 2, 80); mejora.considerar(candidato, evaluacion);
        assertNotNull(mejora.elegido()); assertEquals(1, mejora.aspiraciones()); assertEquals(0, mejora.rechazadosTabu());
        var empate = new CandidateSelector(tabu, 2, 60); empate.considerar(candidato, evaluacion);
        assertNull(empate.elegido()); assertEquals(1, empate.rechazadosTabu()); assertEquals(0, empate.aspiraciones());
        var incompleto = new Solucion(List.of(new Ruta(a, List.of()), new Ruta(b, List.of())));
        var invalido = new CandidateSelector(tabu, 2, 80);
        invalido.considerar(new Candidato(incompleto, candidato.movimiento()), evaluador.evaluar(incompleto));
        assertNull(invalido.elegido()); assertEquals(0, invalido.aspiraciones()); assertEquals(0, invalido.factibles());
    }
    @Test void selectorAceptaEmpeoramientoFactibleParaExplorar() {
        Pedido p = p("P"); var estado = new EstadoOperacion(t, List.of(p), List.of(a, b), List.of(), List.of());
        var s = new Solucion(List.of(new Ruta(a, List.of(p)), new Ruta(b, List.of())));
        var selector = new CandidateSelector(new TabuList(), 1, 60);
        selector.considerar(new Candidato(s, TabuMove.asignacion("P", "TM01", "TA01")), new SolutionEvaluator(estado, estado.pedidos()).evaluar(s));
        assertNotNull(selector.elegido()); assertEquals(80, selector.evaluacionElegida().costoTotal());
    }
}
