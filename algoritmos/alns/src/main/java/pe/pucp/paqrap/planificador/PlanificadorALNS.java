package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.alns.ALNS;
import pe.pucp.paqrap.alns.ParametrosALNS;
import pe.pucp.paqrap.solucion.Solucion;

/**
 * Implementación del componente planificador basada en Adaptive Large Neighborhood Search.
 *
 * <p>Adapta el presupuesto de cómputo al escenario: la operación día a día exige respuesta
 * dentro del ciclo de 15 minutos simulados, mientras que la simulación de 5 días debe completar
 * cientos de ciclos dentro de una ventana de 30 a 60 minutos reales. Como el ALNS es un
 * algoritmo <i>anytime</i>, ajustar el presupuesto no requiere cambiar nada del algoritmo.</p>
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

    /**
     * Configuración recomendada para la operación día a día: presupuesto corto, orientado a
     * responder dentro del ciclo de planificación.
     */
    public static PlanificadorALNS paraOperacionDiaria() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 2_000;
        p.presupuestoMs = 1_500;
        return new PlanificadorALNS(p);
    }

    /**
     * Configuración recomendada para la simulación de 5 días: presupuesto ajustado para que el
     * conjunto de ciclos quepa en la ventana de 30 a 60 minutos reales exigida por LE058.
     */
    public static PlanificadorALNS paraSimulacion5D() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 1_200;
        p.presupuestoMs = 400;
        return new PlanificadorALNS(p);
    }

    /**
     * Configuración recomendada para el escenario de colapso: presupuesto amplio y destrucción
     * conservadora, porque cerca del límite de factibilidad importa más reconstruir bien que
     * explorar lejos.
     */
    public static PlanificadorALNS paraColapso() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 4_000;
        p.presupuestoMs = 3_000;
        p.gradoDestruccionMax = 0.25;
        p.destruccionAdaptativaPorOcupacion = true;
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
