package pe.pucp.paqrap;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import pe.pucp.paqrap.tabu.*;
import pe.pucp.paqrap.alns.estricto.*;

/**
 * Regresiones de medicion, completitud y exportacion pareada con archivos
 * temporales.
 */
public final class ExperimentacionTest {
    static void ok(boolean b, String texto) {
        if (!b)
            throw new AssertionError(texto);
    }

    static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 8, 0);
    static final Nodo N = new Nodo(25, 15);

    static List<PlanificadorEstricto> motores() {
        return List.of(new TabuSearchPlanner(new ConfiguracionTabu(3, 7, 3, 100, 0, 7)),
                new ALNSPlanner(new ConfiguracionALNS(3, 3, 4, 5, .7, .05, 0, 7)));
    }

    public static void main(String[] args) throws Exception {
        var p = new Pedido("p", T, N, 8, 4);
        var a = new PartePedido("a", p, 4);
        var b = new PartePedido("b", p, 4);
        var r1 = new ResultadoRuta(new Ruta("TA01", "A", List.of(a), false), T, T.plusMinutes(60), "A",
                List.of(new Parada(List.of(a), T, T.plusMinutes(30))), List.of(), null, null, 0, 0, List.of());
        var r2 = new ResultadoRuta(new Ruta("TA02", "A", List.of(b), false), T, T.plusMinutes(100), "A",
                List.of(new Parada(List.of(b), T.plusMinutes(40), T.plusMinutes(90))), List.of(), null, null, 0, 0,
                List.of());
        var h = Holguras.calcular(List.of(p), List.of(r1, r2));
        ok(h.completos() == 1 && h.promedioMin() == 150 && h.minimaMin() == 150, "Usar ultima parte y fin de servicio");
        ok(Holguras.calcular(List.of(p), List.of(r1)).promedioMin() == null,
                "No promediar pedido parcialmente entregado");
        var e = new EstadoOperacion(T, List.of(p), List.of(new Vehiculo("TA01", N)),
                List.of(new Almacen("A", N, 100, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
        for (var motor : motores()) {
            var completo = motor.planificar(e, ParametrosOperacion.porDefecto());
            ok(completo.metricas().estadoResultado().equals("COMPLETA"), "Debe completar instancia sencilla");
            ok(completo.metricas().holguraPromedioMin() == 180, "Descanso no debe retrasar entrega innecesariamente");
            ok(completo.metricas().taPrimeraCompletaMs() != null
                    && completo.metricas().taPrimeraCompletaMs() <= completo.metricas().taMs(),
                    "Tiempo primera completa");
            var sinStock = new EstadoOperacion(T, e.pedidos(), e.vehiculos(), List.of(new Almacen("A", N, 0, false)),
                    List.of(), List.of(), List.of(), List.of(), Set.of());
            var colapso = motor.planificar(sinStock, ParametrosOperacion.porDefecto());
            ok(colapso.metricas().colapso() && colapso.metricas().holguraPromedioMin() == null
                    && colapso.metricas().taPrimeraCompletaMs() == null, "Colapso sin ceros artificiales");
            var vacio = new EstadoOperacion(T, List.of(), e.vehiculos(), e.almacenes(), List.of(), List.of(), List.of(),
                    List.of(), Set.of());
            var sinDemanda = motor.planificar(vacio, ParametrosOperacion.porDefecto());
            ok(sinDemanda.metricas().estadoResultado().equals("SIN_DEMANDA")
                    && sinDemanda.metricas().holguraPromedioMin() == null, "Sin demanda no es colapso");
        }
        // Una entrega mas temprana debe ganar aunque cueste mas dinero.
        var rapido = new EvaluadorFactibilidad(e, ParametrosOperacion.porDefecto());
        var partes = rapido.partes();
        var sol = new Solucion(List.of(new Ruta("TA01", "A", partes, false)), List.of());
        var parametros = ParametrosOperacion.porDefecto();
        var costoso = new ParametrosOperacion(parametros.servicioMinutos(), true, parametros.turnoMinutos(),
                parametros.inicioTurnoMinuto(), parametros.descansoDesde(), parametros.descansoHasta(),
                parametros.descansoMinutos(), parametros.tamanioParte(), 1000000000, 1, parametros.velocidades());
        ok(rapido.evaluar(sol).objetivo() == new EvaluadorFactibilidad(e, costoso).evaluar(sol).objetivo(),
                "El costo no debe cambiar el objetivo");
        var parcial = new Solucion(List.of(new Ruta("TA01", "A", List.of(partes.get(0)), false)),
                List.of(partes.get(1)));
        ok(rapido.evaluar(sol).objetivo() < rapido.evaluar(parcial).objetivo(), "Completitud tiene prioridad");
        var tarde = new EstadoOperacion(T, e.pedidos(),
                List.of(new Vehiculo("TA01", TipoVehiculo.TA, N, true, T.plusMinutes(20))), e.almacenes(), List.of(),
                List.of(), List.of(), List.of(), Set.of());
        ok(rapido.evaluar(sol).objetivo() < new EvaluadorFactibilidad(tarde, ParametrosOperacion.porDefecto())
                .evaluar(sol).objetivo(), "Objetivo favorece holgura");
        var dir = Files.createTempDirectory("paqrap-experimento-");
        try {
            var ventas = dir.resolve("ventas.202609.txt");
            var bloqueos = dir.resolve("bloqueo.2609.txt");
            var mant = dir.resolve("mant.txt");
            Files.writeString(ventas, "01d08h00m:25,15,c1,04,4\n");
            Files.writeString(bloqueos, "");
            Files.writeString(mant, "20260901:TA01\n");
            String[] base = { ventas.toString(), bloqueos.toString(), mant.toString(), T.toString() };
            var comando = new ArrayList<String>(List.of(base));
            comando.addAll(List.of(dir.resolve("pares").toString(), "2", "7,8", "0", "--escenario", "E2",
                    "--factor-carga", "2", "--instancia", "caso,con-coma"));
            CompararAlgoritmos.main(comando.toArray(String[]::new));
            var filas = Files.readAllLines(dir.resolve("pares/metricas.csv"));
            ok(filas.size() == 5, "Dos motores por dos semillas");
            ok(Files.readAllLines(dir.resolve("pares/TS-estricto.csv")).size() == 3, "CSV TS separado");
            ok(Files.readAllLines(dir.resolve("pares/ALNS-estricto.csv")).size() == 3, "CSV ALNS separado");
            ok(filas.get(1).contains("\"caso,con-coma\""), "Escapar comas en metadatos CSV");
            String separador = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
            for (int i = 1; i < filas.size(); i += 2) {
                var ts = filas.get(i).split(separador, -1);
                var al = filas.get(i + 1).split(separador, -1);
                ok(ts.length == 39 && al.length == 39, "Ancho CSV igual a cabecera");
                for (int campo : List.of(0, 1, 2, 3, 4, 5, 6, 8))
                    ok(ts[campo].equals(al[campo]), "Par no corresponde a misma instancia y semilla");
            }
            ok(ReporteExperimento.celda("a\"b").equals("\"a\"\"b\""), "Escapar comillas CSV");
            var individual = new ArrayList<String>(List.of(base));
            individual.addAll(List.of("1", "7", "0", "--salida", dir.resolve("individual").toString()));
            EjecutarTabu.main(individual.toArray(String[]::new));
            ok(Files.readAllLines(dir.resolve("individual/TS-estricto.csv")).size() == 2, "CSV individual");
            // La misma entrada y semilla conservan resultados, salvo tiempos de reloj.
            var entrada = new EntradaExperimento(base);
            ok(entrada.estado.mantenimientos().isEmpty() && entrada.estado.averias().isEmpty(),
                    "Excluir incidencias operativas en experimentacion");
            ok(entrada.estado.almacenes().stream().map(Almacen::nodo).toList()
                    .equals(List.of(new Nodo(27, 14), new Nodo(12, 38), new Nodo(57, 27))),
                    "Coordenadas oficiales de almacenes");
            var marcador = base.clone();
            marcador[2] = "-";
            ok(new EntradaExperimento(marcador).estado.equals(entrada.estado), "Marcador sin archivo de mantenimiento");
            var operativo = new pe.pucp.paqrap.estricto.datos.DatasetLoader().cargar(ventas, bloqueos, mant, T, 24,
                    400);
            ok(operativo.estado().mantenimientos().size() == 1, "Conservar soporte operativo de mantenimiento");
            var imposible = new EstadoOperacion(T, entrada.estado.pedidos(), List.of(), entrada.estado.almacenes(),
                    List.of(), List.of(), List.of(), List.of(), Set.of());
            var fallo = motores().get(0).planificar(imposible, ParametrosOperacion.porDefecto());
            var campos = ReporteExperimento.fila(entrada, fallo, 7, 1, 3, 0).stripTrailing().split(separador, -1);
            ok(campos[15].isEmpty() && campos[16].isEmpty() && campos[17].isEmpty(),
                    "Holguras y primera completa deben quedar vacias en colapso");
            for (var motor : motores()) {
                var uno = motor.planificar(entrada.estado, ParametrosOperacion.porDefecto());
                var dos = motor.planificar(entrada.estado, ParametrosOperacion.porDefecto());
                ok(uno.solucion().equals(dos.solucion())
                        && uno.metricas().holguraPromedioMin().equals(dos.metricas().holguraPromedioMin()),
                        "Reproducibilidad");
            }
        } finally {
            // Solo borra el directorio temporal exclusivo creado por esta prueba.
            try (var archivos = Files.walk(dir)) {
                for (var archivo : archivos.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(archivo);
            }
        }
        System.out.println("Pruebas de holgura, colapso, CSV y pares correctas.");
    }
}
