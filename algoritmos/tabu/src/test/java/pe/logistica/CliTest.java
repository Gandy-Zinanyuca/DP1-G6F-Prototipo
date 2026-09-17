package pe.logistica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CliTest {
    @TempDir Path temporal;
    @Test void ayudaYErroresDeArgumentosTienenCodigoDeSalida() {
        var buffer = new ByteArrayOutputStream(); var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(0, Main.ejecutar(new String[]{"--ayuda"}, out, out));
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("--pedidos"));
        assertEquals(2, Main.ejecutar(new String[]{"--desconocido", "1"}, out, out));
        assertEquals(2, Main.ejecutar(new String[]{"--sa"}, out, out));
    }
    @Test void archivosPropiosNoCarganBloqueosNiMantenimientoDemo() throws Exception {
        Path archivo = temporal.resolve("pedidos.txt"); Files.writeString(archivo, "11d13h30m:0,0,P,1,1\n");
        var buffer = new ByteArrayOutputStream(); var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(0, Main.ejecutar(new String[]{"--pedidos", archivo.toString(), "--vehiculos", "TA02", "--central", "0,0", "--tiempo", "2026-09-11T13:30"}, out, out));
        String reporte = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(reporte.contains("Estado: FACTIBLE")); assertTrue(reporte.contains("Pedidos considerados: 1"));
        assertTrue(reporte.contains("Pedidos no asignados: 0")); assertTrue(reporte.contains("Capacidad: 1/24"));
    }
    @Test void pedidoImposibleDevuelveUnoYDiagnostico() throws Exception {
        // Sin --central ni --vehiculos se usa el perfil publicado: central (27,14), flota real de 37.
        // A 41 km del central, ningun tipo de vehiculo llega en el plazo de 1h (ni siquiera 1 unidad):
        // el fraccionamiento no puede rescatar una entrega que nunca llega a tiempo.
        Path archivo = temporal.resolve("pedidos.txt"); Files.writeString(archivo, "11d13h30m:0,0,P,25,1\n");
        var buffer = new ByteArrayOutputStream(); var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(1, Main.ejecutar(new String[]{"--pedidos", archivo.toString(), "--tiempo", "2026-09-11T13:30"}, out, out));
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("Pedido no asignado: V202609-L00001"));
    }
    @Test void pedidoQueExcedeUnVehiculoSeReparteEntreVariosDeLaFlotaPorDefecto() throws Exception {
        // Pedido en el propio central: distancia 0, el unico limite real es la capacidad (TA=24).
        // 25 supera la capacidad de cualquier vehiculo individual pero cabe repartido entre dos.
        Path archivo = temporal.resolve("pedidos.txt"); Files.writeString(archivo, "11d13h30m:27,14,P,25,1\n");
        var buffer = new ByteArrayOutputStream(); var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(0, Main.ejecutar(new String[]{"--pedidos", archivo.toString(), "--tiempo", "2026-09-11T13:30"}, out, out));
        String reporte = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(reporte.contains("Estado: FACTIBLE")); assertTrue(reporte.contains("Pedidos no asignados: 0"));
    }
}
