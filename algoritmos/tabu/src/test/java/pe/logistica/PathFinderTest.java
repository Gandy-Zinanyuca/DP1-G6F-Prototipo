package pe.logistica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.logistica.model.*;
import pe.logistica.routing.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PathFinderTest {
    private final LocalDateTime t = LocalDateTime.of(2026, 9, 11, 13, 30);
    private final Nodo o = new Nodo(0, 0), vecino = new Nodo(1, 0);
    @ParameterizedTest @EnumSource(TipoVehiculo.class)
    void manhattanYVelocidadEnTodaLaReticula(TipoVehiculo tipo) {
        var c = new PathFinder(new GridMap(List.of())).buscar(o, new Nodo(70, 50), t, tipo);
        assertEquals(120, c.distanciaKm());
        assertEquals(t.plusSeconds(120L * 3600 / tipo.velocidadKmh()), c.llegada());
        assertEquals(o, c.coordenadas().get(0)); assertEquals(new Nodo(70, 50), c.coordenadas().get(120));
    }
    @Test void evitaPolilineaEnAmbosSentidos() {
        var mapa = new GridMap(List.of(new Bloqueo(t.minusHours(1), t.plusDays(1), List.of(o, new Nodo(3, 0), new Nodo(3, 2)))));
        var pf = new PathFinder(mapa);
        for (Camino c : List.of(pf.buscar(o, new Nodo(3, 0), t, TipoVehiculo.TA), pf.buscar(new Nodo(3, 0), o, t, TipoVehiculo.TA))) {
            assertTrue(c.distanciaKm() > 3);
            validar(c, mapa, TipoVehiculo.TA);
        }
    }
    @Test void esperaCuandoReabrirEsMasRapidoQueDesviar() {
        var mapa = new GridMap(List.of(new Bloqueo(t, t.plusSeconds(10), List.of(o, vecino))));
        Camino c = new PathFinder(mapa).buscar(o, vecino, t, TipoVehiculo.TA);
        assertEquals(1, c.distanciaKm()); assertEquals(t.plusSeconds(100), c.llegada());
        assertEquals(t.plusSeconds(10), c.pasos().get(0).salida()); validar(c, mapa, TipoVehiculo.TA);
    }
    @Test void bloqueoQueComienzaDuranteCruceExigeDesvio() {
        var mapa = new GridMap(List.of(new Bloqueo(t.plusSeconds(30), t.plusMinutes(5), List.of(o, vecino))));
        Camino c = new PathFinder(mapa).buscar(o, vecino, t, TipoVehiculo.TA);
        assertEquals(3, c.distanciaKm()); assertEquals(t.plusSeconds(270), c.llegada()); validar(c, mapa, TipoVehiculo.TA);
    }
    @Test void bloqueoSolapadoYLimitesSemiabiertos() {
        var mapa = new GridMap(List.of(new Bloqueo(t, t.plusMinutes(2), List.of(o, vecino)),
                new Bloqueo(t.plusMinutes(1), t.plusMinutes(4), List.of(vecino, o))));
        assertEquals(t.plusMinutes(4), mapa.proximaSalida(o, vecino, t, Duration.ofSeconds(90)));
        assertTrue(mapa.cruceValido(o, vecino, t.minusSeconds(90), t));
        assertTrue(mapa.cruceValido(vecino, o, t.plusMinutes(4), t.plusMinutes(5)));
        assertFalse(mapa.cruceValido(o, vecino, t.minusSeconds(1), t.plusSeconds(1)));
    }
    @Test void origenTemporalmenteEncerradoPuedeEsperar() {
        var mapa = new GridMap(List.of(new Bloqueo(t, t.plusMinutes(5), List.of(vecino, o, new Nodo(0, 1)))));
        Camino c = new PathFinder(mapa).buscar(o, vecino, t, TipoVehiculo.TA);
        assertEquals(1, c.distanciaKm()); assertEquals(t.plusSeconds(390), c.llegada()); validar(c, mapa, TipoVehiculo.TA);
    }
    @Test void origenIgualDestinoNoConsumeTiempo() {
        Camino c = new PathFinder(new GridMap(List.of())).buscar(o, o, t, TipoVehiculo.TB);
        assertEquals(0, c.distanciaKm()); assertEquals(t, c.llegada()); assertEquals(List.of(o), c.coordenadas());
    }
    static void validar(Camino c, GridMap mapa, TipoVehiculo tipo) {
        Nodo nodo = c.origen(); LocalDateTime hora = c.inicio();
        for (PasoCamino p : c.pasos()) {
            assertEquals(nodo, p.origen()); assertFalse(p.salida().isBefore(hora));
            assertEquals(Duration.ofSeconds(3600 / tipo.velocidadKmh()), Duration.between(p.salida(), p.llegada()));
            assertTrue(mapa.cruceValido(p.origen(), p.destino(), p.salida(), p.llegada()));
            nodo = p.destino(); hora = p.llegada();
        }
        assertEquals(c.llegada(), hora);
    }
}
