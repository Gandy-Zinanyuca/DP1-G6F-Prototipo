package pe.pucp.paqrap;

import pe.pucp.paqrap.estricto.modelo.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Regresiones de continuidad operativa compartida, sin depender de la data privada. */
public final class SimulacionComparadaTest {
    private static void ok(boolean valor, String mensaje) {
        if (!valor) throw new AssertionError(mensaje);
    }
    public static void main(String[] args) throws Exception {
        var t = LocalDateTime.of(2026, 9, 1, 8, 0);
        var n = new Nodo(25, 15);
        var base = new EstadoOperacion(t, List.of(),
                List.of(new Vehiculo("TB01", n), new Vehiculo("TB02", n)),
                List.of(new Almacen("A", n, 16, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
        var datos = new SimulacionComparada.Datos(base, List.of(
                new Pedido("p1", t, n, 8, 4), new Pedido("p2", t.plusHours(2), n, 8, 4)));
        var dir = Files.createTempDirectory("simulacion-comparable-");
        try {
            int i = 0;
            for (var motor : ExperimentacionTest.motores()) {
                var csv = dir.resolve("corrida-" + i++ + ".csv");
                var r = SimulacionComparada.ejecutar(datos, motor, 10, 0, csv, 7, 1);
                ok(r.fin().equals("FIN_DE_DATOS"), "Agotar datos despues de entregas y retornos");
                ok(r.completos() == 2 && r.paquetesEntregados() == 16, "No duplicar partes entre ciclos");
                ok(r.holguraMedia() == 180, "Fin de servicio de la ultima parte determina holgura");
                ok(r.ejecuciones() == 2 && r.vehiculos() == 2, "Reutilizar vehiculos tras retorno");
                for (var linea : Files.readAllLines(csv)) ok(linea.split(",", -1).length == 19, "CSV uniforme incluso sin demanda");
                var repetido = SimulacionComparada.ejecutar(datos, motor, 10, 0, dir.resolve("repite-" + i + ".csv"), 7, 1);
                ok(repetido.completos() == r.completos() && repetido.instante().equals(r.instante()), "Restaurar condiciones entre corridas");
                var limite = SimulacionComparada.ejecutar(datos, motor, 10, 1, dir.resolve("limite-" + i + ".csv"), 7, 1);
                ok(limite.fin().equals("LIMITE_DE_CICLOS") && limite.completos() == 0, "Despacho no equivale a entrega");
                var colapso = SimulacionComparada.ejecutar(datos, motor, 10, 0, dir.resolve("colapso-" + i + ".csv"), 7, 2);
                ok(colapso.fin().equals("COLAPSO_PLANIFICACION") && colapso.completos() == 0, "No despachar plan incompleto");
            }
            ok(base.almacenes().get(0).stock() == 16, "La simulacion no muta la instancia");
            System.out.println("Pruebas de simulacion TS/ALNS correctas.");
        } finally {
            try (var archivos = Files.walk(dir)) {
                for (var archivo : archivos.sorted(Comparator.reverseOrder()).toList()) Files.delete(archivo);
            }
        }
    }
}
