package pe.logistica;

import org.junit.jupiter.api.Test;
import pe.logistica.data.*;
import pe.logistica.model.*;
import pe.logistica.routing.GridMap;
import pe.logistica.tabu.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlannerTest {
    private final LocalDateTime t = LocalDateTime.of(2026, 9, 11, 13, 30);
    private final Vehiculo auto = new Vehiculo("TA01", new Nodo(0, 0));
    private final ConfiguracionTabu config = new ConfiguracionTabu(30, 4, 100, 7);
    private Pedido p(String id, LocalDateTime momento, int cantidad) { return new Pedido(id, momento, new Nodo(0, 0), cantidad, 12); }
    private EstadoOperacion estado(List<Pedido> pedidos, List<Vehiculo> flota) { return new EstadoOperacion(t, pedidos, flota, List.of(), List.of()); }
    @Test void ventanaIncluyeAmbosExtremosYExcluyeElExterior() {
        var pedidos = List.of(p("Antes", t.minusNanos(1), 1), p("Inicio", t, 1),
                p("Fin", t.plusMinutes(120), 1), p("Despues", t.plusMinutes(120).plusNanos(1), 1));
        var resultado = new TabuSearchPlanner().ejecutar(estado(pedidos, List.of(auto)), config);
        assertTrue(resultado.metricas().factibilidadGlobal()); assertEquals(2, resultado.metricas().pedidosConsiderados());
        assertEquals(Set.of("Inicio", "Fin"), new HashSet<>(resultado.mejorSolucion().rutas().get(0).pedidos().stream().map(Pedido::id).toList()));
        assertEquals(120, resultado.metricas().scMinutos());
    }
    @Test void inicialInviableEsExplicitaYNoBuscaConPenalizaciones() {
        var resultado = new TabuSearchPlanner().ejecutar(estado(List.of(p("A", t, 25)), List.of(auto)), config);
        var m = resultado.metricas(); assertFalse(m.factibilidadGlobal()); assertEquals(1, m.pedidosNoAsignados());
        assertEquals(0, m.rendimiento().iteraciones()); assertEquals(0, m.rendimiento().solucionesFactiblesEvaluadas());
        assertEquals("SOLUCION_INICIAL_NO_FACTIBLE", m.rendimiento().motivoParada());
    }
    @Test void reportaPlanParcialCuandoFallaUnaInsercionPosterior() {
        var resultado = new TabuSearchPlanner().ejecutar(estado(List.of(p("A", t, 1), p("B", t, 25)), List.of(auto)), config);
        assertFalse(resultado.metricas().factibilidadGlobal()); assertEquals(1, resultado.metricas().pedidosAsignados());
        assertEquals(1, resultado.metricas().pedidosNoAsignados()); assertEquals(50, resultado.metricas().porcentajeCumplimiento());
    }
    @Test void sinPedidosEsFactibleSinDivisionesPorCero() {
        var m = new TabuSearchPlanner().ejecutar(estado(List.of(), List.of()), config).metricas();
        assertTrue(m.factibilidadGlobal()); assertEquals(100, m.porcentajeCumplimiento());
        assertEquals(0, m.vehiculosUtilizados()); assertEquals(0, m.distanciaPromedioPorVehiculo());
        assertEquals(0, m.porcentajePromedioUtilizacionCapacidad()); assertEquals(3, m.distribucionPorTipo().size());
    }
    @Test void sinFlotaNoPuedeAsignarPedidos() {
        assertFalse(new TabuSearchPlanner().ejecutar(estado(List.of(p("A", t, 1)), List.of()), config).metricas().factibilidadGlobal());
    }
    @Test void paradaSinVecinosYLimiteCeroSonCoherentes() {
        var estado = estado(List.of(p("A", t, 1)), List.of(auto));
        var soloInicial = new TabuSearchPlanner().ejecutar(estado, new ConfiguracionTabu(30, 4, 0, 7));
        assertEquals(0, soloInicial.metricas().rendimiento().iteraciones());
        var sinVecinos = new TabuSearchPlanner().ejecutar(estado, config);
        assertEquals(1, sinVecinos.metricas().rendimiento().iteraciones());
        assertEquals("SIN_CANDIDATO_ADMISIBLE", sinVecinos.metricas().rendimiento().motivoParada());
    }
    @Test void validaParametrosEIdentificadoresDuplicados() {
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionTabu(0, 1, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionTabu(30, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionTabu(30, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionTabu(30, 1, 10, 0));
        assertThrows(ArithmeticException.class, () -> new ConfiguracionTabu(Long.MAX_VALUE, 2, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> estado(List.of(p("A", t, 1), p("A", t, 2)), List.of(auto)));
        assertThrows(IllegalArgumentException.class, () -> estado(List.of(), List.of(auto, auto)));
    }
    @Test void demoMejoraCostoConservaFactibilidadYProduceMetricasAuditables() throws Exception {
        Path demo = Path.of("src/main/resources/demo");
        var pedidos = new PedidoParser().leer(demo.resolve("pedidos.txt"), 2026, 9);
        var flota = List.of(auto, new Vehiculo("TA02", new Nodo(0, 0)), new Vehiculo("TM01", new Nodo(0, 0)),
                new Vehiculo("TM02", new Nodo(0, 0)), new Vehiculo("TB01", new Nodo(0, 0)));
        var estado = new EstadoOperacion(t, pedidos, flota, new BloqueoParser().leer(demo.resolve("bloqueos.txt"), 2026, 9),
                new MantenimientoParser().leer(demo.resolve("mantenimientos.txt")));
        var resultado = new TabuSearchPlanner().ejecutar(estado, config); var m = resultado.metricas(); var r = m.rendimiento();
        assertTrue(m.factibilidadGlobal()); assertEquals(8, m.pedidosConsiderados()); assertEquals(8, m.pedidosAsignados());
        assertEquals(8, m.pedidosDentroDelPlazo()); assertEquals(100, m.porcentajeCumplimiento());
        assertTrue(m.costoTotal() < r.costoSolucionInicial()); assertEquals(100, r.iteraciones());
        assertTrue(r.iteracionMejorSolucion() > 0); assertTrue(r.candidatosEvaluados() >= r.solucionesFactiblesEvaluadas());
        assertTrue(r.movimientosTabuRechazados() > 0); assertTrue(m.taMs() > 0);
        assertEquals(m.costoTotal(), m.distribucionPorTipo().values().stream().mapToDouble(x -> x.costo()).sum());
        assertEquals(m.distanciaTotalKm(), m.distribucionPorTipo().values().stream().mapToDouble(x -> x.distanciaKm()).sum());
        assertEquals(0L, m.capacidadUtilizadaPorVehiculo().get("TA02"));
        assertEquals(m.distanciaTotalKm() / m.vehiculosUtilizados(), m.distanciaPromedioPorVehiculo());
        var considerados = pedidos.stream().filter(p -> !p.fechaRegistro().isBefore(t) && !p.fechaRegistro().isAfter(t.plusMinutes(120))).toList();
        assertTrue(new SolutionEvaluator(estado, considerados).evaluar(resultado.mejorSolucion()).factible());
        var mapa = new GridMap(estado.bloqueos());
        for (var ruta : resultado.evaluacion().rutas()) for (var camino : ruta.caminos())
            PathFinderTest.validar(camino, mapa, ruta.ruta().vehiculo().tipo());
        // Reutilizar el planner no hereda tabu, cache ni contadores de otra ejecucion.
        var otra = new TabuSearchPlanner().ejecutar(estado, config);
        assertEquals(resultado.mejorSolucion(), otra.mejorSolucion());
        assertEquals(m.rendimiento().candidatosEvaluados(), otra.metricas().rendimiento().candidatosEvaluados());
    }
}
