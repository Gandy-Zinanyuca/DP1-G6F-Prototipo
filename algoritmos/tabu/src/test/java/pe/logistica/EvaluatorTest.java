package pe.logistica;

import org.junit.jupiter.api.Test;
import pe.logistica.model.*;
import pe.logistica.tabu.SolutionEvaluator;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {
    private final LocalDateTime t = LocalDateTime.of(2026, 9, 11, 13, 30);
    private final Nodo o = new Nodo(0, 0);
    private final Vehiculo auto = new Vehiculo("TA01", o);
    private Pedido pedido(String id, Nodo nodo, int cantidad, int plazo) { return new Pedido(id, t, nodo, cantidad, plazo); }
    private EvaluacionSolucion evaluar(List<Pedido> requeridos, List<Ruta> rutas, List<Mantenimiento> mantenimiento) {
        var estado = new EstadoOperacion(t, requeridos, List.of(auto), List.of(), mantenimiento);
        return new SolutionEvaluator(estado, requeridos).evaluar(new Solucion(rutas));
    }
    @Test void rechazaOmisionesDuplicadosPedidosAlteradosYVehiculosAlterados() {
        Pedido p = pedido("P", o, 1, 2);
        assertFalse(evaluar(List.of(p), List.of(new Ruta(auto, List.of())), List.of()).factible());
        assertFalse(evaluar(List.of(p), List.of(new Ruta(auto, List.of(p, p))), List.of()).factible());
        assertFalse(evaluar(List.of(p), List.of(new Ruta(auto, List.of(pedido("P", o, 2, 2)))), List.of()).factible());
        assertFalse(evaluar(List.of(p), List.of(new Ruta(new Vehiculo("TM01", o), List.of(p))), List.of()).factible());
    }
    @Test void unVehiculoNoPuedeTenerDosRutas() {
        Pedido p = pedido("P", o, 1, 2);
        assertFalse(evaluar(List.of(p), List.of(new Ruta(auto, List.of(p)), new Ruta(auto, List.of())), List.of()).factible());
    }
    @Test void capacidadEsSumaDeBultosSinRecargas() {
        Pedido a = pedido("A", o, 12, 4), b = pedido("B", o, 13, 4);
        assertFalse(evaluar(List.of(a, b), List.of(new Ruta(auto, List.of(a, b))), List.of()).factible());
    }
    @Test void deadlineExigeFinalizarServicioYPermiteIgualdad() {
        Pedido exacto = pedido("A", o, 1, 1), tarde = pedido("B", new Nodo(1, 0), 1, 1);
        assertTrue(evaluar(List.of(exacto), List.of(new Ruta(auto, List.of(exacto))), List.of()).factible());
        assertFalse(evaluar(List.of(tarde), List.of(new Ruta(auto, List.of(tarde))), List.of()).factible());
    }
    @Test void mideServicioViajeCostoYRegreso() {
        Pedido p = pedido("P", new Nodo(4, 0), 1, 3);
        var e = evaluar(List.of(p), List.of(new Ruta(auto, List.of(p))), List.of());
        assertTrue(e.factible()); assertEquals(8, e.distanciaTotalKm()); assertEquals(64, e.costoTotal());
        assertEquals(1.2, e.tiempoTotalHoras(), 1e-9);
        assertEquals(t.plusMinutes(6), e.rutas().get(0).visitas().get(0).inicioAtencion());
        assertEquals(t.plusMinutes(72), e.rutas().get(0).fin());
    }
    @Test void salidaEsPosteriorALaDisponibilidadYTodaLaCarga() {
        Pedido a = pedido("A", o, 1, 10), b = new Pedido("B", t.plusHours(2), o, 1, 10);
        Vehiculo v = new Vehiculo("TA01", TipoVehiculo.TA, o, true, t.plusHours(1));
        var estado = new EstadoOperacion(t, List.of(a, b), List.of(v), List.of(), List.of());
        var e = new SolutionEvaluator(estado, estado.pedidos()).evaluar(new Solucion(List.of(new Ruta(v, List.of(a, b)))));
        assertTrue(e.factible()); assertEquals(t.plusHours(2), e.rutas().get(0).salida());
    }
    @Test void rechazaVehiculoNoDisponible() {
        Pedido p = pedido("P", o, 1, 2);
        Vehiculo v = new Vehiculo("TA01", TipoVehiculo.TA, o, false, t);
        var estado = new EstadoOperacion(t, List.of(p), List.of(v), List.of(), List.of());
        assertFalse(new SolutionEvaluator(estado, estado.pedidos()).evaluar(new Solucion(List.of(new Ruta(v, List.of(p))))).factible());
    }
    @Test void mantenimientoImpideSalidaPeroPermiteRutaVacia() {
        Pedido p = pedido("P", o, 1, 2); var m = List.of(new Mantenimiento(t.toLocalDate(), "TA01"));
        assertFalse(evaluar(List.of(p), List.of(new Ruta(auto, List.of(p))), m).factible());
        assertTrue(evaluar(List.of(), List.of(new Ruta(auto, List.of())), m).factible());
    }
    @Test void compruebaMantenimientoTambienEnElRegresoTrasMedianoche() {
        LocalDateTime noche = t.withHour(21).withMinute(0);
        Pedido p = new Pedido("P", noche, new Nodo(70, 0), 1, 6);
        var estado = new EstadoOperacion(noche, List.of(p), List.of(auto), List.of(), List.of(new Mantenimiento(t.toLocalDate().plusDays(1), "TA01")));
        var e = new SolutionEvaluator(estado, estado.pedidos()).evaluar(new Solucion(List.of(new Ruta(auto, List.of(p)))));
        assertFalse(e.factible()); assertTrue(e.rutas().get(0).visitas().get(0).dentroDelPlazo());
    }
    @Test void bloqueoPuedeVolverInviableUnPlazo() {
        // TA tarda 1.5 min por calle: 40 km + servicio = 2 h exactas; el desvio rompe el plazo.
        Pedido p = pedido("P", new Nodo(40, 0), 1, 2);
        var bloqueo = new Bloqueo(t.minusHours(1), t.plusDays(1), List.of(o, new Nodo(1, 0)));
        var estado = new EstadoOperacion(t, List.of(p), List.of(auto), List.of(bloqueo), List.of());
        assertFalse(new SolutionEvaluator(estado, estado.pedidos()).evaluar(new Solucion(List.of(new Ruta(auto, List.of(p))))).factible());
    }
}
