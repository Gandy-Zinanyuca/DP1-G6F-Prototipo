package pe.pucp.paqrap.estricto.servicios;
import pe.pucp.paqrap.estricto.modelo.*;
import java.util.*;
public final class Resultados {
 public static ResultadoPlanificacion crear(String nombre,Solucion s,EvaluadorFactibilidad ev,long inicio,
   int iter,long candidatos,long iniciales,String parada) {
  var evaluacion=ev.evaluar(s);
  if(!evaluacion.factible()) throw new IllegalStateException("Salida invalida: "+evaluacion.errores());
  var cantidades=new HashMap<String,Integer>();int carga=0,capacidad=0,usados=0;
  for(var r:s.rutas()) if(!r.partes().isEmpty()) {
   usados++;carga+=r.carga();
   capacidad+=ev.estado().vehiculos().stream().filter(v->v.codigo().equals(r.vehiculo())).findFirst().orElseThrow().tipo().capacidad();
   for(var p:r.partes()) cantidades.merge(p.pedido().id(),p.cantidad(),Integer::sum);
  }
  int completos=0,total=ev.estado().pedidos().size(),paquetes=0;
  for(var p:ev.estado().pedidos()) {paquetes+=p.cantidad();if(cantidades.getOrDefault(p.id(),0)==p.cantidad())completos++;}
  var m=new MetricasResultado((System.nanoTime()-inicio)/1e6,evaluacion.objetivo(),evaluacion.costo(),
   evaluacion.rutas().stream().mapToDouble(ResultadoRuta::distanciaKm).sum(),
   evaluacion.rutas().stream().mapToDouble(ResultadoRuta::minutos).sum(),total==0?1:(double)completos/total,
   paquetes==0?1:(double)carga/paquetes,usados,capacidad==0?0:(double)carga/capacidad,iter,candidatos,iniciales,
   total,completos,evaluacion.paquetesPendientes(),parada);
  return new ResultadoPlanificacion(nombre,s,evaluacion,m);
 }
 private Resultados() {}
}
