package pe.pucp.paqrap.estricto.servicios;
import pe.pucp.paqrap.estricto.modelo.*;
import java.util.*;
/** Validacion comun de rutas y conservacion exacta de partes; pendientes no son entregas factibles. */
public final class EvaluadorFactibilidad {
 private final EstadoOperacion estado; private final ParametrosOperacion par;
 private final Map<String,PartePedido> partes=new LinkedHashMap<>();
 private final CalculadorRuta calculador;
 private long evaluaciones;
 private final Map<Ruta,ResultadoRuta> cache=new LinkedHashMap<>(256,.75f,true) {
  protected boolean removeEldestEntry(Map.Entry<Ruta,ResultadoRuta> e) { return size()>1500; }
 };
 public EvaluadorFactibilidad(EstadoOperacion e,ParametrosOperacion p) {
  estado=e;par=p;calculador=new CalculadorRuta(e,p);
  for(var parte:GestorCapacidad.dividir(e,p)) partes.put(parte.id(),parte);
 }
 public EstadoOperacion estado() { return estado; }
 public ParametrosOperacion parametros() { return par; }
 public List<PartePedido> partes() { return List.copyOf(partes.values()); }
 public long evaluaciones() { return evaluaciones; }
 public EvaluacionSolucion evaluar(Solucion s) {
  evaluaciones++;
  var errores=new ArrayList<String>();var resultados=new ArrayList<ResultadoRuta>();
  var vistos=new HashSet<String>();var vehiculos=new HashSet<String>();var stock=new HashMap<String,Long>();
  for(var r:s.rutas()) {
   boolean conocido=estado.vehiculos().stream().anyMatch(v->v.codigo().equals(r.vehiculo())) &&
     estado.almacenes().stream().anyMatch(a->a.id().equals(r.almacenOrigen()));
   if(!conocido || !vehiculos.add(r.vehiculo())) {errores.add("Unidad/almacen desconocido o ruta duplicada");continue;}
   if(r.enCurso() && !estado.rutasEnCurso().contains(r)) errores.add("Ruta en curso alterada");
   for(var parte:r.partes()) validarParte(parte,vistos,errores);
   if(!r.enCurso()) stock.merge(r.almacenOrigen(),(long)r.carga(),Long::sum);
   var rr=cache.computeIfAbsent(r,calculador::calcular);resultados.add(rr);errores.addAll(rr.errores());
  }
  long pendientes=0;
  for(var parte:s.pendientes()) {validarParte(parte,vistos,errores);pendientes+=parte.cantidad();}
  if(!vistos.equals(partes.keySet())) errores.add("Partes perdidas o ajenas al estado");
  for(var a:estado.almacenes()) if(!a.ilimitado() && stock.getOrDefault(a.id(),0L)>a.stock()) errores.add("Stock excedido: "+a.id());
  double costo=resultados.stream().mapToDouble(ResultadoRuta::costo).sum();
  return new EvaluacionSolucion(resultados,errores,errores.isEmpty()?costo+pendientes*par.penalizacionPaquetePendiente():Double.POSITIVE_INFINITY,Math.toIntExact(pendientes));
 }
 private void validarParte(PartePedido p,Set<String> vistos,List<String> errores) {
  if(!p.equals(partes.get(p.id())) || !vistos.add(p.id())) errores.add("Parte duplicada o alterada: "+p.id());
 }
 public ResultadoRuta evaluarRuta(Ruta r) { return cache.computeIfAbsent(r,calculador::calcular); }
}
