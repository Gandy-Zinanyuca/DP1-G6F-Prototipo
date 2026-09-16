package pe.logistica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.logistica.data.*;
import pe.logistica.model.*;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class ParserTest {
    @TempDir Path temporal;
    @Test void interpretaPedidoMensualYDeadline() {
        var p = new PedidoParser().parsear("11d13h31m:45,43,c9167,12,36", 2026, 9);
        assertEquals(LocalDateTime.of(2026, 9, 11, 13, 31), p.fechaRegistro());
        assertEquals(LocalDateTime.of(2026, 9, 13, 1, 31), p.deadline());
        assertEquals(new Nodo(45, 43), p.ubicacion()); assertEquals("c9167", p.id()); assertEquals(12, p.cantidad());
    }
    @Test void interpretaPolilineaYMantenimiento() {
        var b = new BloqueoParser().parsear("01d00h03m-01d20h31m:15,10,30,10,30,18", 2026, 9);
        assertEquals(3, b.puntos().size()); assertEquals(LocalDateTime.of(2026, 9, 1, 20, 31), b.fin());
        assertEquals(new Mantenimiento(LocalDate.of(2026, 9, 1), "TA01"), new MantenimientoParser().parsear("20260901:TA01"));
    }
    @ParameterizedTest @ValueSource(strings = {
            "31d13h31m:45,43,p,12,36", "11d24h00m:1,1,p,1,2", "11d13h31m:71,43,p,12,36",
            "11d13h31m:45,-1,p,12,36", "11d13h31m:45,43,p,0,36", "11d13h31m:45,43,p,1,0",
            "11d13h31m:45,43,,1,2", "11d13h31m:45,43,p,1,2,3"})
    void rechazaPedidoInvalido(String texto) {
        assertThrows(RuntimeException.class, () -> new PedidoParser().parsear(texto, 2026, 9));
    }
    @ParameterizedTest @ValueSource(strings = {
            "01d00h00m-01d01h00m:0,0,1,1", "01d00h00m-01d01h00m:0,0,0,0",
            "01d01h00m-01d00h00m:0,0,0,1", "01d00h00m-01d01h00m:0,0,1"})
    void rechazaBloqueoInvalido(String texto) {
        assertThrows(IllegalArgumentException.class, () -> new BloqueoParser().parsear(texto, 2026, 9));
    }
    @ParameterizedTest @ValueSource(strings = {"20260230:TA01", "20260901:TX01", "20260901:TA1", "2026091:TA01"})
    void rechazaMantenimientoInvalido(String texto) {
        assertThrows(RuntimeException.class, () -> new MantenimientoParser().parsear(texto));
    }
    @Test void archivoConBomComentariosYErrorUbicable() throws Exception {
        Path p = temporal.resolve("pedidos.txt");
        Files.writeString(p, "\uFEFF# comentario\n\n11d13h31m:45,43,p,1,2\n");
        assertEquals(1, new PedidoParser().leer(p, 2026, 9).size());
        Files.writeString(p, "# comentario\nlinea rota\n");
        var ex = assertThrows(IllegalArgumentException.class, () -> new PedidoParser().leer(p, 2026, 9));
        assertTrue(ex.getMessage().contains("pedidos.txt:2:"));
    }
    @Test void validaTipoCodigoYBordes() {
        assertEquals(TipoVehiculo.TB, TipoVehiculo.desdeCodigo("TB10"));
        assertEquals(120, new Nodo(0, 0).manhattan(new Nodo(70, 50)));
        assertThrows(IllegalArgumentException.class, () -> new Vehiculo("TA01", TipoVehiculo.TM, new Nodo(0, 0), true, LocalDateTime.MIN));
        assertThrows(IllegalArgumentException.class, () -> new Nodo(70, 51));
    }
}
