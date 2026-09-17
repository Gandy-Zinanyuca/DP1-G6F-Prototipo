package pe.pucp.paqrap;

import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/** Punto de entrada de consola para ejecutar únicamente Tabu Search estricto. */
public final class EjecutarTabu {
    public static void main(String[] args) throws Exception {
        var entrada = EjecutorIndividual.cargar(args, "EjecutarTabu");
        var configuracion = new ConfiguracionTabu(
                entrada.iteraciones(), 7, Math.max(1, entrada.iteraciones()),
                400, 0, entrada.semilla());
        EjecutorIndividual.ejecutar(entrada, new TabuSearchPlanner(configuracion));
    }

    private EjecutarTabu() {}
}
