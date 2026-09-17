package pe.pucp.paqrap;

import pe.pucp.paqrap.alns.estricto.ALNSPlanner;
import pe.pucp.paqrap.alns.estricto.ConfiguracionALNS;

/** Punto de entrada de consola para ejecutar únicamente ALNS estricto. */
public final class EjecutarALNS {
    public static void main(String[] args) throws Exception {
        var entrada = EjecutorIndividual.cargar(args, "EjecutarALNS");
        var configuracion = new ConfiguracionALNS(
                entrada.iteraciones(), Math.max(1, entrada.iteraciones()),
                4, 5, 0.7, 0.05, 0, entrada.semilla());
        EjecutorIndividual.ejecutar(entrada, new ALNSPlanner(configuracion));
    }

    private EjecutarALNS() {}
}
