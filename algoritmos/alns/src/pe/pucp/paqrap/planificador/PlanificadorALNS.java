package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.alns.ALNS;
import pe.pucp.paqrap.alns.ParametrosALNS;
import pe.pucp.paqrap.solucion.Solucion;

/**
 * Implementación del componente planificador basada en Adaptive Large Neighborhood Search.
 *
 * <p>Las configuraciones por escenario solo cambian parámetros de ConfiguracionALNS; los
 * valores deben calibrarse mediante experimentación numérica (ISA 5.2).</p>
 */
public class PlanificadorALNS implements Planificador {

    private final ParametrosALNS parametros;
    private ALNS motor;
    private ALNS.Estadisticas ultimasEstadisticas;

    public PlanificadorALNS() {
        this(new ParametrosALNS());
    }

    public PlanificadorALNS(ParametrosALNS parametros) {
        this.parametros = parametros;
        this.motor = new ALNS(parametros);
    }

    /** Operación día a día. */
    public static PlanificadorALNS paraOperacionDiaria() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 1_000;
        return new PlanificadorALNS(p);
    }

    /** Simulación de 5 días: menos iteraciones por ejecución. */
    public static PlanificadorALNS paraSimulacion5D() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 200;
        return new PlanificadorALNS(p);
    }

    /** Escenario de colapso: más iteraciones y destrucción conservadora. */
    public static PlanificadorALNS paraColapso() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 2_000;
        p.proporcionDestruccion = 0.10;
        return new PlanificadorALNS(p);
    }

    @Override
    public Solucion planificar(ContextoPlanificacion ctx, Solucion planPrevio) {
        Solucion s = motor.resolver(ctx, planPrevio);
        ultimasEstadisticas = motor.getEstadisticas();
        return s;
    }

    @Override
    public String nombre() {
        return "ALNS";
    }

    @Override
    public String resumenUltimaEjecucion() {
        return ultimasEstadisticas == null ? "(sin ejecuciones)" : ultimasEstadisticas.toString();
    }

    public ALNS.Estadisticas getUltimasEstadisticas() {
        return ultimasEstadisticas;
    }

    public ParametrosALNS getParametros() {
        return parametros;
    }

    /** Reinicia el motor, útil para repetir un escenario con una semilla distinta. */
    public void reiniciarMotor(long semilla) {
        parametros.semilla = semilla;
        this.motor = new ALNS(parametros);
    }
}
