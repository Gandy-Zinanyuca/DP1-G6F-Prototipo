package pe.logistica.tabu;

import pe.logistica.model.EvaluacionSolucion;

/** Admision: primero factibilidad, luego tabu/aspiracion, finalmente menor costo. */
public final class CandidateSelector {
    private final TabuList tabu;
    private final int iteracion;
    private final double mejorCostoGlobal;
    private Candidato elegido;
    private EvaluacionSolucion evaluacionElegida;
    private long evaluados, factibles, rechazadosTabu, aspiraciones;
    public CandidateSelector(TabuList tabu, int iteracion, double mejorCostoGlobal) {
        this.tabu = tabu; this.iteracion = iteracion; this.mejorCostoGlobal = mejorCostoGlobal;
    }
    public void considerar(Candidato candidato, EvaluacionSolucion evaluacion) {
        evaluados++;
        if (!evaluacion.factible()) return;
        factibles++;
        if (tabu.esTabu(candidato.movimiento(), iteracion)) {
            if (evaluacion.costoTotal() < mejorCostoGlobal) aspiraciones++;
            else { rechazadosTabu++; return; }
        }
        if (elegido == null || evaluacion.costoTotal() < evaluacionElegida.costoTotal()) {
            elegido = candidato; evaluacionElegida = evaluacion;
        }
    }
    public Candidato elegido() { return elegido; }
    public EvaluacionSolucion evaluacionElegida() { return evaluacionElegida; }
    public long evaluados() { return evaluados; }
    public long factibles() { return factibles; }
    public long rechazadosTabu() { return rechazadosTabu; }
    public long aspiraciones() { return aspiraciones; }
}
