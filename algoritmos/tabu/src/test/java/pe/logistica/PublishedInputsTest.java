package pe.logistica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.logistica.data.*;
import pe.logistica.model.*;
import pe.logistica.routing.*;
import pe.logistica.tabu.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishedInputsTest {
    @TempDir Path temporal;
    private final LocalDateTime t = LocalDateTime.of(2026, 9, 9, 0, 0);
    @Test void clientesRepetidosConservanPedidosIndependientesYEstables() throws Exception {
        Path archivo = temporal.resolve("ventas.202609.txt");
        Files.writeString(archivo, "09d00h00m:1,1,c001,2,4\n09d00h00m:1,1,c001,2,4\n");
        var pedidos = new PedidoParser().leer(archivo, 2026, 9);
        assertEquals(2, pedidos.size()); assertNotEquals(pedidos.get(0).id(), pedidos.get(1).id());
        assertEquals("c001", pedidos.get(0).clienteId()); assertEquals("c001", pedidos.get(1).clienteId());
        assertEquals(pedidos, new PedidoParser().leer(archivo, 2026, 9));
    }
    @Test void flotaYPosicionesCorrespondenALaPublicacionMasReciente() {
        var flota = ReferenciaProyecto.flota(ReferenciaProyecto.CENTRAL);
        assertEquals(37, flota.size()); assertTrue(flota.stream().allMatch(v -> v.ubicacionInicial().equals(new Nodo(27, 14))));
        assertEquals(10, flota.stream().filter(v -> v.tipo() == TipoVehiculo.TA).count());
        assertEquals(15, flota.stream().filter(v -> v.tipo() == TipoVehiculo.TM).count());
        assertEquals(12, flota.stream().filter(v -> v.tipo() == TipoVehiculo.TB).count());
        assertEquals(new Nodo(12, 38), ReferenciaProyecto.NOROESTE); assertEquals(new Nodo(57, 27), ReferenciaProyecto.ESTE);
    }
    @Test void plazoDeLlegadaExcluyeServicioPeroServicioOcupaElVehiculo() {
        var v = new Vehiculo("TA01", new Nodo(0, 0));
        var pedido = new Pedido("P", t, new Nodo(20, 0), 1, 1);
        var estado = new EstadoOperacion(t, List.of(pedido), List.of(v), List.of(), List.of(), ParametrosOperacion.publicados());
        var ev = new SolutionEvaluator(estado, estado.pedidos()).evaluar(new Solucion(List.of(new Ruta(v, List.of(new Entrega(pedido, 1))))));
        assertTrue(ev.factible()); var visita = ev.rutas().get(0).visitas().get(0);
        assertEquals(t.plusHours(1), visita.llegada()); assertEquals(t.plusHours(2), visita.finAtencion());
        assertEquals(3, ev.tiempoTotalHoras());
        var tarde = new Pedido("P2", t, new Nodo(21, 0), 1, 1);
        var otro = new EstadoOperacion(t, List.of(tarde), List.of(v), List.of(), List.of(), ParametrosOperacion.publicados());
        assertFalse(new SolutionEvaluator(otro, otro.pedidos()).evaluar(new Solucion(List.of(new Ruta(v, List.of(new Entrega(tarde, 1)))))).factible());
    }
    @Test void velocidadFraccionariaNoTruncaSegundosNiPrometeLlegadaAnticipada() {
        var pf = new PathFinder(new GridMap(List.of()));
        var camino = pf.buscar(new Nodo(0, 0), new Nodo(14, 0), t, 14.0);
        long nanos = Duration.between(t, camino.llegada()).toNanos();
        assertTrue(nanos >= Duration.ofHours(1).toNanos());
        assertTrue(nanos - Duration.ofHours(1).toNanos() < 14);
        assertEquals(t.plusMinutes(30), pf.buscar(new Nodo(0, 0), new Nodo(20, 0), t, 40.0).llegada());
        assertEquals(t.plusHours(1), pf.buscar(new Nodo(0, 0), new Nodo(20, 0), t, 20.0).llegada());
    }
    @Test void unNodoBloqueadoTampocoPermiteCruzarEnSentidoPerpendicular() {
        var b = new Bloqueo(t, t.plusHours(1), List.of(new Nodo(1, 1), new Nodo(1, 2)));
        var mapa = new GridMap(List.of(b), true);
        var camino = new PathFinder(mapa).buscar(new Nodo(0, 1), new Nodo(2, 1), t, 20.0);
        assertEquals(4, camino.distanciaKm()); assertFalse(camino.coordenadas().contains(new Nodo(1, 1)));
        var espera = new PathFinder(mapa).buscar(new Nodo(1, 1), new Nodo(1, 1), t, 20.0);
        assertEquals(t.plusHours(1), espera.llegada());
    }
    @Test void mantenimientoRepiteElTxtCadaDosMesesIncluyendoAnioBisiesto() throws Exception {
        var parser = new MantenimientoParser(); var base = parser.leer(Path.of("datos/mant.preventivo.09.10.txt"));
        var calendario = parser.expandirBimensual(base, 2026, 2029);
        assertEquals(37, base.size()); assertEquals(888, calendario.size());
        assertTrue(calendario.containsAll(base));
        assertTrue(calendario.contains(new Mantenimiento(LocalDate.of(2026, 1, 3), "TM07")));
        assertTrue(calendario.contains(new Mantenimiento(LocalDate.of(2028, 2, 28), "TA10")));
        assertTrue(calendario.contains(new Mantenimiento(LocalDate.of(2029, 12, 28), "TA10")));
        assertFalse(calendario.contains(new Mantenimiento(LocalDate.of(2026, 9, 2), "TM07")));
    }
    @Test void todosLosArchivosPublicadosPasanLosParsersJava() throws Exception {
        long ventas = 0, bloqueos = 0; var ids = new HashSet<String>();
        try (var archivos = Files.list(Path.of("datos/ventas"))) {
            for (Path archivo : archivos.sorted().toList()) {
                String fecha = archivo.getFileName().toString().split("\\.")[1];
                var pedidos = new PedidoParser().leer(archivo, Integer.parseInt(fecha.substring(0,4)), Integer.parseInt(fecha.substring(4)));
                ventas += pedidos.size(); for (Pedido p : pedidos) assertTrue(ids.add(p.id()));
            }
        }
        try (var archivos = Files.list(Path.of("datos/bloqueos"))) {
            for (Path archivo : archivos.sorted().toList()) {
                String fecha = archivo.getFileName().toString().split("\\.")[1];
                bloqueos += new BloqueoParser().leer(archivo, 2000 + Integer.parseInt(fecha.substring(0,2)), Integer.parseInt(fecha.substring(2))).size();
            }
        }
        assertEquals(160010, ventas); assertEquals(21725, bloqueos);
    }
    @Test void cargaVentanaRealYRespetaElMantenimientoDelDia() throws Exception {
        var datos = new DatasetLoader().cargar(Path.of("datos"), t, 120);
        assertEquals(19, datos.pedidos().size()); assertEquals(888, datos.mantenimientos().size());
        var estado = new EstadoOperacion(t, datos.pedidos(), ReferenciaProyecto.flota(ReferenciaProyecto.CENTRAL),
                datos.bloqueos(), datos.mantenimientos(), ParametrosOperacion.publicados());
        var resultado = new TabuSearchPlanner().ejecutar(estado, new ConfiguracionTabu(30,4,3,7));
        assertTrue(resultado.metricas().factibilidadGlobal()); assertEquals(19, resultado.metricas().pedidosDentroDelPlazo());
        assertEquals(0L, resultado.metricas().capacidadUtilizadaPorVehiculo().get("TM09"));
        assertEquals(20.0, resultado.parametros().velocidad(TipoVehiculo.TA));
    }
    @Test void ventanaQueCruzaMesLeeLosDosArchivos() throws Exception {
        // Fixture explicito: el TXT real de septiembre termina el dia 29.
        Files.createDirectories(temporal.resolve("ventas")); Files.createDirectories(temporal.resolve("bloqueos"));
        Files.writeString(temporal.resolve("ventas/ventas.202609.txt"), "30d23h59m:27,14,c001,1,4\n");
        Files.writeString(temporal.resolve("ventas/ventas.202610.txt"), "01d00h00m:27,14,c001,1,4\n");
        Files.writeString(temporal.resolve("mant.preventivo.09.10.txt"), "20260901:TA01\n20261004:TA02\n");
        var datos = new DatasetLoader().cargar(temporal, LocalDateTime.of(2026,9,30,23,30), 120);
        assertTrue(datos.pedidos().stream().anyMatch(p -> p.fechaRegistro().getMonthValue()==9));
        assertTrue(datos.pedidos().stream().anyMatch(p -> p.fechaRegistro().getMonthValue()==10));
    }
    @Test void configuracionResuelveRutasRelativasYPermiteSobrescribirPorCli() throws Exception {
        Path config = temporal.resolve("escenario.properties"), pedidos = temporal.resolve("ventas.txt");
        Files.writeString(pedidos, "09d00h00m:27,14,c001,1,1\n");
        Files.writeString(config, "pedidos=ventas.txt\ntiempo=2026-09-09T00:00\niteraciones=0\nvelocidad-ta=20\n");
        var buffer = new ByteArrayOutputStream(); var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(0, Main.ejecutar(new String[]{"--config", config.toString(), "--velocidad-ta", "30"}, out, out));
        String texto = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("Pedidos considerados: 1")); assertTrue(texto.contains("Velocidad TA: 30.000"));
        assertTrue(texto.contains("cliente=c001"));
        Files.writeString(config, "parametro-inventado=1\n");
        assertEquals(2, Main.ejecutar(new String[]{"--config", config.toString()}, out, out));
    }
}
