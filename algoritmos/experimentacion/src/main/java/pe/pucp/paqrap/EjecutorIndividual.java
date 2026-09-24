package pe.pucp.paqrap;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;

final class EjecutorIndividual {
    record Entrada(EntradaExperimento experimento, int iteraciones, long semilla, long presupuestoMs) {
    }

    static Entrada cargar(String[] args, String clase) throws Exception {
        var e = new EntradaExperimento(args);
        if (e.args.length > 7)
            throw new IllegalArgumentException("Uso: " + clase
                    + " ventas bloqueos - instante [iteraciones] [semilla] [presupuesto-ms] [--opcion valor]");
        return new Entrada(e, e.iteraciones(4, 100), e.args.length > 5 ? Long.parseLong(e.args[5]) : 20262,
                e.presupuesto(6));
    }

    static void ejecutar(Entrada entrada, PlanificadorEstricto motor) throws Exception {
        var e = entrada.experimento();
        var salida = ReporteExperimento.directorio(e.opciones.getOrDefault("salida",
                "algoritmos/experimentacion/resultados/individual-" + UUID.randomUUID()));
        var r = motor.planificar(e.estado, ParametrosOperacion.porDefecto());
        ReporteExperimento.auditar(e, r);
        ReporteExperimento.consola(e, r, entrada.semilla(), 1);
        String contenido = ReporteExperimento.CABECERA
                + ReporteExperimento.fila(e, r, entrada.semilla(), 1, entrada.iteraciones(), entrada.presupuestoMs());
        Files.writeString(salida.resolve(r.algoritmo() + ".csv"), contenido, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        ReporteExperimento.metadatos(salida, e, entrada.iteraciones(), entrada.presupuestoMs(),
                Long.toString(entrada.semilla()));
        ReporteExperimento.detalle(salida, r, entrada.semilla(), 1);
        System.out.println("CSV: " + salida.toAbsolutePath().resolve(r.algoritmo() + ".csv"));
    }

    private EjecutorIndividual() {
    }
}
