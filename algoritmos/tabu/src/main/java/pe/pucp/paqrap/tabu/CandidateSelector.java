package pe.pucp.paqrap.tabu;
import pe.pucp.paqrap.estricto.modelo.*;
/** Adaptado de planificador-tabu: la aspiracion nunca relaja restricciones duras. */
public final class CandidateSelector {
 private final TabuList tabu;private final int iter;private final double mejor;
 private Candidato elegido;private EvaluacionSolucion evaluacion;
 public CandidateSelector(TabuList tabu,int iter,double mejor) {this.tabu=tabu;this.iter=iter;this.mejor=mejor;}
 public void considerar(Candidato c,EvaluacionSolucion e) {
  if(!e.factible() || (tabu.esTabu(c.movimiento(),iter) && e.objetivo()>=mejor-1e-9))return;
  if(elegido==null || e.objetivo()<evaluacion.objetivo()) {elegido=c;evaluacion=e;}
 }
 public Candidato elegido(){return elegido;}
 public EvaluacionSolucion evaluacion(){return evaluacion;}
}
