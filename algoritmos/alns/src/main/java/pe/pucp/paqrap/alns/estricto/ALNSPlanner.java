package pe.pucp.paqrap.alns.estricto;
import pe.pucp.paqrap.alns.SelectorAdaptativo;
import pe.pucp.paqrap.alns.CriterioAceptacion;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import java.util.*;
/** Adaptacion estricta: reutiliza ruleta y aceptacion del ALNS original, con factibilidad compartida. */
public final class ALNSPlanner implements PlanificadorEstricto {
 private final ConfiguracionALNS config;
 public ALNSPlanner(){this(ConfiguracionALNS.porDefecto());}
 public ALNSPlanner(ConfiguracionALNS c){config=Objects.requireNonNull(c);}
 public ResultadoPlanificacion planificar(EstadoOperacion estado,ParametrosOperacion parametros) {
  long inicio=System.nanoTime();var ev=new EvaluadorFactibilidad(estado,parametros);
  Solucion actual=GeneradorSolucionInicial.generar(ev),mejor=actual;
  if(!ev.evaluar(actual).factible())throw new IllegalStateException("Inicial invalida");
  long iniciales=ev.evaluaciones();double costo=ev.evaluar(actual).objetivo(),mejorCosto=costo;
  var random=new Random(config.semilla());
  var destroy=new SelectorAdaptativo<Integer>(List.of(0,1),1.0, config.reaccion());
  var repair=new SelectorAdaptativo<Boolean>(List.of(false,true),1.0,config.reaccion());
  var parametrosAceptacion=new pe.pucp.paqrap.alns.ParametrosALNS();
  parametrosAceptacion.temperaturaInicial=Math.max(1,ev.evaluar(actual).rutas().stream().mapToDouble(ResultadoRuta::costo).sum()*config.aceptacionInicial());
  parametrosAceptacion.factorEnfriamiento=Math.pow(.01,1.0/Math.max(1,config.maxIteraciones()));
  var aceptacion=new CriterioAceptacion(parametrosAceptacion);
  int iter=0,sinMejora=0;long candidatos=0;String parada="MAX_ITERACIONES";
  while(iter<config.maxIteraciones()) {
   if(config.presupuestoMs()>0 && (System.nanoTime()-inicio)/1_000_000>=config.presupuestoMs()){parada="TIEMPO";break;}
   if(estado.pedidos().isEmpty()){parada="SIN_PEDIDOS";break;}
   iter++;long antes=ev.evaluaciones();int d=destroy.seleccionar(random),r=repair.seleccionar(random);
   var removibles=new ArrayList<PartePedido>();
   for(var ruta:actual.rutas())if(!ruta.enCurso())removibles.addAll(ruta.partes());
   if(d==0)Collections.shuffle(removibles,random);
   else if(!removibles.isEmpty()) {
    var pivote=removibles.get(random.nextInt(removibles.size())).pedido().ubicacion();
    removibles.sort(Comparator.comparingInt(p->p.pedido().ubicacion().manhattan(pivote)));
   }
   var quitar=new HashSet<>(removibles.subList(0,Math.min(config.destruccionMax(),removibles.size())));
   var rutas=new ArrayList<Ruta>();var pendientes=new ArrayList<>(actual.pendientes());
   for(var ruta:actual.rutas()){
    var lista=new ArrayList<PartePedido>();
    for(var parte:ruta.partes())if(quitar.contains(parte))pendientes.add(parte);else lista.add(parte);
    rutas.add(ruta.conPartes(lista));
   }
   var candidata=GeneradorSolucionInicial.reparar(new Solucion(rutas,pendientes),ev,repair.operador(r),random);
   var ec=ev.evaluar(candidata);candidatos+=ev.evaluaciones()-antes;double premio=0;
   if(ec.factible()) {
    if(ec.objetivo()<mejorCosto-1e-9){mejor=candidata;mejorCosto=ec.objetivo();sinMejora=0;premio=8;}
    else sinMejora++;
    if(aceptacion.evaluar(costo,ec.objetivo(),iter,random).aceptar){premio=Math.max(premio,ec.objetivo()<costo?4:1);actual=candidata;costo=ec.objetivo();}
   } else sinMejora++;
   destroy.puntuar(d,premio);repair.puntuar(r,premio);
   if(iter%config.segmento()==0){destroy.actualizarPesos();repair.actualizarPesos();}
   if(sinMejora>=config.sinMejoraMax()){parada="ESTANCAMIENTO";break;}
  }
  return Resultados.crear("ALNS-estricto",mejor,ev,inicio,iter,candidatos,iniciales,parada);
 }
}
