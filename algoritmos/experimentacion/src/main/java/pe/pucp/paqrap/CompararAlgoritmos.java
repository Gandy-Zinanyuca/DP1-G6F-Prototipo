package pe.pucp.paqrap;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import pe.pucp.paqrap.tabu.*;
import pe.pucp.paqrap.alns.estricto.*;

/**
 * Una combinacion escenario/carga/instancia; cada semilla produce un par
 * TS-ALNS.
 */
public final class CompararAlgoritmos {
    public static void main(String[] args) throws Exception {
        var e = new EntradaExperimento(args);
        if (e.args.length < 5 || e.args.length > 8 || e.opciones.containsKey("salida"))
            throw new IllegalArgumentException(
                    "Uso: CompararAlgoritmos ventas bloqueos - instante salida [iteraciones] [semillas-con-comas] [presupuesto-ms] [--opcion valor]");
        int iter = e.iteraciones(5, 20);
        long presupuesto = e.presupuesto(7);
        String semillas = e.args.length > 6 ? e.args[6] : "20262,20263,20264";
        var seeds = new ArrayList<Long>();
        for (String semilla : semillas.split(",", -1))
            seeds.add(Long.parseLong(semilla.trim()));
        if (new HashSet<>(seeds).size() != seeds.size())
            throw new IllegalArgumentException("Semillas repetidas; usar semillas distintas para las repeticiones");
        var salida = ReporteExperimento.directorio(e.args[4]);
        var par = ParametrosOperacion.porDefecto();
        var ev = new EvaluadorFactibilidad(e.estado, par);
        var inicial = GeneradorSolucionInicial.generar(ev);
        double objetivoInicial = ev.evaluar(inicial).objetivo();
        new TabuSearchPlanner(new ConfiguracionTabu(2, 7, 2, 400, 0, 20262)).planificar(e.estado, par);
        new ALNSPlanner(new ConfiguracionALNS(2, 2, 4, 5, .7, .05, 0, 20262)).planificar(e.estado, par);
        ReporteExperimento.metadatos(salida, e, iter, presupuesto, semillas);
        Files.writeString(salida.resolve("README.md"),
                "\nCalentamiento: 2 iteraciones por motor. Orden: TS y despues ALNS por semilla. Estado inmutable compartido; caches y aleatoriedad nuevos por corrida.\nObjetivo inicial: "
                        + objetivoInicial + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        for (String nombre : List.of("metricas", "TS-estricto", "ALNS-estricto"))
            Files.writeString(salida.resolve(nombre + ".csv"), ReporteExperimento.CABECERA, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        int repeticion = 0;
        var colapsos = new LinkedHashMap<String, Integer>();
        var completas = new LinkedHashMap<String, Integer>();
        for (long seed : seeds) {
            repeticion++;
            String estadoAntes = e.estado.toString();
            var motores = List.<PlanificadorEstricto>of(
                    // sinMejoraMax es el umbral de diversificacion de TS, no un corte: debe
                    // dispararse varias veces dentro del presupuesto de iteraciones.
                    new TabuSearchPlanner(new ConfiguracionTabu(iter, 7, Math.max(5, iter / 10), 400, presupuesto, seed)),
                    new ALNSPlanner(new ConfiguracionALNS(iter, Math.max(1, iter), 4, 5, .7, .05, presupuesto, seed)));
            for (var motor : motores) {
                var r = motor.planificar(e.estado, par);
                if (!e.estado.toString().equals(estadoAntes))
                    throw new AssertionError("Estado original alterado");
                ReporteExperimento.auditar(e, r);
                if (r.evaluacion().objetivo() > objetivoInicial + 1e-9)
                    throw new AssertionError("Empeora inicial comun");
                var fila = ReporteExperimento.fila(e, r, seed, repeticion, iter, presupuesto);
                for (String nombre : List.of("metricas", r.algoritmo()))
                    Files.writeString(salida.resolve(nombre + ".csv"), fila, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND);
                ReporteExperimento.consola(e, r, seed, repeticion);
                ReporteExperimento.detalle(salida, r, seed, repeticion);
                colapsos.merge(r.algoritmo(), r.metricas().colapso() ? 1 : 0, Integer::sum);
                completas.merge(r.algoritmo(), r.metricas().estadoResultado().equals("COMPLETA") ? 1 : 0, Integer::sum);
            }
        }
        System.out.println("Resultados pareados: " + salida.toAbsolutePath());
        for (var nombre : colapsos.keySet())
            System.out.printf(Locale.ROOT, "%s: completas %d/%d | colapsos %d/%d | sin demanda %d%n", nombre,
                    completas.get(nombre), seeds.size(), colapsos.get(nombre), seeds.size(),
                    seeds.size() - completas.get(nombre) - colapsos.get(nombre));
    }
}
