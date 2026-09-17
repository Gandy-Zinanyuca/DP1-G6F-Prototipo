package pe.pucp.paqrap;

import java.util.*;
import pe.pucp.paqrap.alns.ParametrosALNS;
import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.*;
import pe.pucp.paqrap.planificador.*;
import pe.pucp.paqrap.servicios.ConstructorInicial;
import pe.pucp.paqrap.solucion.*;

public class IntegracionALNSTest {
    /** Pruebas sin dependencias externas; cualquier fallo termina con código distinto de cero. */
    public static void main(String[] args) {
        IntegracionALNSTest pruebas = new IntegracionALNSTest();
        pruebas.conservaPedidosCapacidadEInventarioYNoEmpeoraInicial();
        pruebas.reproduceAsignacionesConSemillaEIteracionesFijas();
        pruebas.caracterizaBrechasDePlazosYPedidosParciales();
        System.out.println("3 pruebas de integración correctas.");
    }

    private static void assertTrue(boolean condicion) {
        if (!condicion) throw new AssertionError("Se esperaba una condición verdadera");
    }

    private static void assertFalse(boolean condicion) {
        assertTrue(!condicion);
    }

    private static void assertEquals(Object esperado, Object obtenido) {
        if (!Objects.equals(esperado, obtenido)) {
            throw new AssertionError("Esperado: " + esperado + "; obtenido: " + obtenido);
        }
    }
    private ContextoPlanificacion contexto() {
        Almacen almacen = new Almacen("A", new Coordenada(10, 10), false, 30);
        List<Pedido> pedidos = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            pedidos.add(new Pedido(i, "C" + i, new Coordenada(10 + i, 10), 2, 0, 18));
        }
        Instancia instancia = new Instancia(new MapaUrbano(List.of()), List.of(almacen),
                Instancia.construirFlota(almacen, 2, 0, 0), pedidos, List.of(), 2026, 1);
        return ContextoPlanificacion.construir(instancia, 0, pedidos, List.of(), new ParametrosPlanificador());
    }

    private PlanificadorALNS motor() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 40;
        p.presupuestoMs = Long.MAX_VALUE;
        return new PlanificadorALNS(p);
    }

    void conservaPedidosCapacidadEInventarioYNoEmpeoraInicial() {
        ContextoPlanificacion ctx = contexto();
        double inicial = ConstructorInicial.construir(ctx).getCosto();
        Planificador planificador = motor();
        Solucion s = planificador.planificar(ctx, null);
        assertTrue(s.getCosto() <= inicial);
        Set<Integer> ids = new HashSet<>();
        for (Ruta r : s.getRutas()) {
            assertTrue(r.getCargaTotal() <= r.getVehiculo().getCapacidad());
            for (Pedido p : r.getSecuencia()) assertTrue(ids.add(p.getId()));
        }
        for (Pedido p : s.getNoAsignados()) assertTrue(ids.add(p.getId()));
        assertEquals(ctx.getPedidosPorAtender().size(), ids.size());
        for (Almacen a : ctx.getAlmacenes()) {
            assertTrue(s.consumo(a) <= ctx.stockInicial(a));
            assertEquals(30, a.getStock());
        }
    }

    void reproduceAsignacionesConSemillaEIteracionesFijas() {
        PlanificadorALNS a = motor(), b = motor();
        Solucion sa = a.planificar(contexto(), null), sb = b.planificar(contexto(), null);
        assertEquals(sa.toString(), sb.toString());
        assertEquals(40, a.getUltimasEstadisticas().iteraciones);
        assertEquals(40, b.getUltimasEstadisticas().iteraciones);
    }

    void caracterizaBrechasDePlazosYPedidosParciales() {
        ContextoPlanificacion ctx = contexto();
        Pedido grande = new Pedido(99, "C99", new Coordenada(11, 10), 25, 0, 18);
        Pedido vencido = new Pedido(100, "C100", new Coordenada(11, 10), 1, -300, 4);
        ContextoPlanificacion caso = new ContextoPlanificacion(ctx.getInstancia(), 0,
                List.of(grande, vencido), ctx.getUnidadesAsignables(), ctx.getParametros());
        Solucion s = ConstructorInicial.construir(caso);
        assertTrue(s.getNoAsignados().contains(grande));
        assertEquals(1, s.getPedidosTardios());
        assertFalse(caso.getParametros().limitarRutaAlTurno);
    }
}
