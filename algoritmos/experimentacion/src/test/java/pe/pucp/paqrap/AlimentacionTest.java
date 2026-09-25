package pe.pucp.paqrap;

import java.time.*;
import java.util.*;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;

/** Fronteras de alimentacion y conservacion de actividad real entre ciclos. */
public final class AlimentacionTest {
    static void verificar(boolean valor, String mensaje) {
        if (!valor)
            throw new AssertionError(mensaje);
    }

    public static void main(String[] args) throws Exception {
        var par = ParametrosOperacion.porDefecto();
        var n = new Nodo(25, 15);
        var dia = LocalDate.of(2026, 1, 20);
        for (int hora : new int[] { 13, 21, 5 }) {
            var t = dia.atTime(hora, 0);
            var e = new EstadoOperacion(t, List.of(new Pedido("p", t, n, 1, 4)), List.of(new Vehiculo("TA01", n)),
                    List.of(new Almacen("A", n, 10, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
            var ev = new EvaluadorFactibilidad(e, par);
            var rr = ev.evaluarRuta(new Ruta("TA01", "A", ev.partes(), false));
            verificar(rr.factible() && rr.paradas().get(0).finServicio().equals(t.plusHours(1)),
                    "Entrega antes de alimentacion tardia");
            verificar(rr.descansoInicio().equals(t.plusHours(1)) && rr.descansoFin().equals(t.plusHours(2)),
                    "Alimentacion hasta cambio de turno");
        }
        var t = dia.atTime(7, 0);
        var base = new EstadoOperacion(t, List.of(), List.of(new Vehiculo("TA01", n)),
                List.of(new Almacen("A", n, 100, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
        var finales = new HashMap<String, LocalDateTime>();
        verificar(SimulacionComparada.descansosCompletados(base, base.vehiculos(), dia.atTime(8, 59), par, finales)
                .isEmpty(), "No acreditar 59 minutos");
        verificar(SimulacionComparada.descansosCompletados(base, base.vehiculos(), dia.atTime(9, 0), par, finales)
                .contains("TA01"), "Acreditar hora ociosa completa");
        verificar(SimulacionComparada.descansosCompletados(base, base.vehiculos(), dia.atTime(15, 0), par, finales)
                .isEmpty(), "No heredar descanso de otro turno");
        finales.clear();
        var ocupada = List.of(new Vehiculo("TA01", TipoVehiculo.TA, n, true, dia.atTime(12, 0)));
        verificar(SimulacionComparada.descansosCompletados(base, ocupada, dia.atTime(12, 30), par, finales).isEmpty(),
                "No contar tiempo de ruta como ocio");
        verificar(SimulacionComparada.descansosCompletados(base, ocupada, dia.atTime(13, 0), par, finales)
                .contains("TA01"), "Descanso despues del retorno");
        finales.clear();
        finales.put("TA01", dia.atTime(13, 0));
        verificar(SimulacionComparada.descansosCompletados(base, ocupada, dia.atTime(12, 30), par, finales).isEmpty(),
                "No acreditar descanso futuro comprometido");
        // Pedido real de la linea 352, aislado con una unidad que estuvo libre por la
        // mañana.
        var pedido = new Pedido("V202601-L00352", dia.atTime(12, 2), new Nodo(46, 6), 7, 4);
        var archivo = java.nio.file.Files.createTempDirectory("paqrap-alimentacion-");
        try {
            int i = 0;
            for (var motor : ExperimentacionTest.motores()) {
                var resultado = SimulacionComparada.ejecutar(new SimulacionComparada.Datos(base, List.of(pedido)),
                        motor, 10, 0, archivo.resolve("motor-" + i++ + ".csv"), 7, 1);
                verificar(
                        resultado.fin().equals("FIN_DE_DATOS") && resultado.completos() == 1
                                && resultado.paquetesEntregados() == 7,
                        "Pedido de las 12:02 debe completarse con alimentacion previa");
            }
        } finally {
            try (var archivos = java.nio.file.Files.walk(archivo)) {
                for (var ruta : archivos.sorted(Comparator.reverseOrder()).toList())
                    java.nio.file.Files.delete(ruta);
            }
        }
        System.out.println("Pruebas de alimentacion (tres turnos e inactividad) correctas.");
    }
}
