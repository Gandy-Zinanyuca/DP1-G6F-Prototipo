package pe.logistica;

import org.junit.jupiter.api.Test;
import pe.logistica.model.*;
import pe.logistica.tabu.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SplitNeighborhoodTest {
    private final LocalDateTime t = LocalDateTime.of(2026, 9, 11, 13, 30);
    private final Vehiculo a = new Vehiculo("TA01", new Nodo(0, 0)), b = new Vehiculo("TA02", new Nodo(0, 0));
    private Pedido p(String id, int cantidad) { return new Pedido(id, t, new Nodo(5, 0), cantidad, 10); }

    @Test void generaUnCandidatoPorRutaDestinoConHuecoParcial() {
        Pedido p1 = p("P1", 20);
        var s = new Solucion(List.of(new Ruta(a, List.of(new Entrega(p1, 20))), new Ruta(b, List.of(new Entrega(p("P2", 15), 15)))));
        // TA02 tiene 15/24 usados: 9 libres, menor que los 20 de P1 -> fraccionable.
        // (El vecindario tambien explora la direccion inversa -fraccionar P2 hacia TA01-, que se ignora aqui con el filtro.)
        List<Candidato> vecinos = new ArrayList<>(); new SplitNeighborhood().generar(s, vecinos::add);
        var deP1 = vecinos.stream().filter(c -> c.movimiento().pedidoA().equals("P1")).toList();
        assertFalse(deP1.isEmpty());
        for (Candidato c : deP1) {
            var origen = c.solucion().rutas().get(0).entregas();
            var destino = c.solucion().rutas().get(1).entregas();
            int enOrigen = origen.stream().filter(e -> e.pedido().id().equals("P1")).mapToInt(Entrega::cantidad).sum();
            int enDestino = destino.stream().filter(e -> e.pedido().id().equals("P1")).mapToInt(Entrega::cantidad).sum();
            assertEquals(20, enOrigen + enDestino);
            assertEquals(9, enDestino);
        }
    }

    @Test void noFraccionaSiElDestinoNoTieneHuecoOYaAlcanzaParaElTotal() {
        // Ambos vehiculos llenos: ninguna direccion (P1->TA02 ni P2->TA01) tiene hueco.
        var lleno = new Solucion(List.of(new Ruta(a, List.of(new Entrega(p("P1", 24), 24))),
                new Ruta(b, List.of(new Entrega(p("P2", 24), 24)))));
        List<Candidato> vecinosLleno = new ArrayList<>(); new SplitNeighborhood().generar(lleno, vecinosLleno::add);
        assertTrue(vecinosLleno.isEmpty());
        // TA02 vacio (hueco >= cantidad completa de P1): lo cubre AssignmentNeighborhood, no SplitNeighborhood.
        var conHuecoTotal = new Solucion(List.of(new Ruta(a, List.of(new Entrega(p("P1", 20), 20))), new Ruta(b, List.of())));
        List<Candidato> vecinosHuecoTotal = new ArrayList<>(); new SplitNeighborhood().generar(conHuecoTotal, vecinosHuecoTotal::add);
        assertTrue(vecinosHuecoTotal.isEmpty());
    }

    @Test void noFraccionaEntregasDeUnaSolaUnidad() {
        var s = new Solucion(List.of(new Ruta(a, List.of(new Entrega(p("P1", 1), 1))), new Ruta(b, List.of())));
        List<Candidato> vecinos = new ArrayList<>(); new SplitNeighborhood().generar(s, vecinos::add);
        assertTrue(vecinos.isEmpty());
    }
}
