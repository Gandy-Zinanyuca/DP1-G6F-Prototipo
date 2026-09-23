package pe.pucp.paqrap;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import pe.pucp.paqrap.estricto.caminos.*;
import pe.pucp.paqrap.tabu.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Regresiones ejecutables sin JUnit; falla con codigo distinto de cero. */
public final class RestriccionesTabuTest {
 static final LocalDateTime T=LocalDate.of(2026,9,1).atTime(8,0);
 static final Nodo N=new Nodo(25,15);
 static int pruebas;
 static void ok(boolean c,String m){if(!c)throw new AssertionError(m);}
 static ParametrosOperacion parametros(int servicio) {
  var p=ParametrosOperacion.porDefecto();
  return new ParametrosOperacion(servicio,true,480,0,60,420,60,4,50,1e6,p.velocidades());
 }
 static EstadoOperacion estado(List<Pedido> pedidos,List<Vehiculo> flota,List<Almacen> almacenes,
    List<Bloqueo> bloqueos,List<Averia> averias,List<Mantenimiento> mantenimientos,List<Ruta> activas,Set<String> descanso) {
  return new EstadoOperacion(T,pedidos,flota,almacenes,bloqueos,averias,mantenimientos,activas,descanso);
 }
 static EstadoOperacion simple(List<Pedido> pedidos) {
  return estado(pedidos,List.of(new Vehiculo("TA01",N),new Vehiculo("TA02",N)),
   List.of(new Almacen("A",N,100,false)),List.of(),List.of(),List.of(),List.of(),Set.of());
 }
 static Pedido pedido(String id,int cantidad){return new Pedido(id,T,N,cantidad,4);}
 static Solucion una(EvaluadorFactibilidad ev) {
  return new Solucion(List.of(new Ruta("TA01","A",ev.partes(),false)),List.of());
 }
 static TabuSearchPlanner tabu() {return new TabuSearchPlanner(new ConfiguracionTabu(6,3,6,100,0,7));}
 public static void main(String[] args) {
  testTabu();testAspiracion();testDivision();testPlazo();testTurno();testDescanso();testMantenimiento();
  testAveria();testStock();testBloqueos();testIntegridad();testVecindarios();testReplanificacion();
  testReproducibilidad();testValidacionEntrada();
  System.out.println(pruebas+" grupos de pruebas estrictas correctos.");
 }
 static void testTabu(){
  var l=new TabuList();var ida=TabuMove.asignacion("p","a","b");
  l.registrar(ida,5,3);ok(l.esTabu(TabuMove.asignacion("p","b","a"),6),"inversa no tabu");
  ok(l.esTabu(TabuMove.asignacion("p","c","a"),8),"atributo expirado antes");
  ok(!l.esTabu(TabuMove.asignacion("p","b","a"),9),"tabu no expira");
  var swap=TabuMove.swap("v","a","b");l.registrar(swap,1,2);
  ok(l.esTabu(TabuMove.swap("v","b","a"),2),"swap inverso");
  l.registrar(TabuMove.relocate("v","a",0,2),1,2);
  ok(l.esTabu(TabuMove.relocate("v","a",2,0),2),"relocate inverso");pruebas++;
 }
 static void testAspiracion(){
  var l=new TabuList();l.registrar(TabuMove.asignacion("p","a","b"),1,3);
  var c=new Candidato(new Solucion(List.of(),List.of()),TabuMove.asignacion("p","b","a"));
  var selector=new CandidateSelector(l,2,100);
  selector.considerar(c,new EvaluacionSolucion(List.of(),List.of(),101,0));ok(selector.elegido()==null,"tabu aceptado");
  selector.considerar(c,new EvaluacionSolucion(List.of(),List.of("plazo"),90,0));ok(selector.elegido()==null,"aspiracion infactible");
  selector.considerar(c,new EvaluacionSolucion(List.of(),List.of(),90,0));ok(selector.elegido()!=null,"aspiracion no aplicada");pruebas++;
 }
 static void testDivision(){
  var e=simple(List.of(pedido("grande",30)));var resultado=tabu().planificar(e,parametros(10));
  ok(resultado.evaluacion().completa(),"pedido grande no dividido");
  ok(resultado.metricas().vehiculosUsados()==2,"division entre vehiculos");
  ok(resultado.solucion().rutas().stream().flatMap(r->r.partes().stream()).mapToInt(PartePedido::cantidad).sum()==30,"cantidad perdida");pruebas++;
 }
 static void testPlazo(){
  var vencido=new Pedido("vencido",T.minusHours(5),N,2,4);
  var e=simple(List.of(vencido));var r=tabu().planificar(e,parametros(60));
  ok(r.evaluacion().factible()&&!r.evaluacion().completa()&&r.metricas().paquetesPendientes()==2,"plazo blando");
  var p=new Pedido("fin",T.minusHours(3).minusMinutes(30),N,1,4);
  var ev=new EvaluadorFactibilidad(simple(List.of(p)),parametros(60));
  ok(!ev.evaluar(una(ev)).factible(),"fin servicio fuera de plazo admitido");pruebas++;
 }
 static void testTurno(){
  var v=new Vehiculo("TA01",TipoVehiculo.TA,N,true,T.plusHours(7).plusMinutes(30));
  var p=new Pedido("p",T,N,1,18);
  var e=estado(List.of(p),List.of(v),List.of(new Almacen("A",N,5,false)),List.of(),List.of(),List.of(),List.of(),Set.of("TA01"));
  var ev=new EvaluadorFactibilidad(e,parametros(60));var siguiente=ev.evaluar(una(ev)).rutas().get(0);
  ok(siguiente.factible()&&siguiente.salida().equals(T.plusHours(8)),"no espera siguiente turno");
  ok(siguiente.descansoInicio()!=null,"turno futuro hereda descanso realizado");
  var tarde=new Vehiculo("TA01",TipoVehiculo.TA,N,true,T.plusHours(6).plusMinutes(30));
  var estandar=estado(List.of(p),List.of(tarde),e.almacenes(),List.of(),List.of(),List.of(),List.of(),Set.of("TA01"));
  var defecto=new EvaluadorFactibilidad(estandar,ParametrosOperacion.porDefecto());
  var turno15=defecto.evaluar(una(defecto)).rutas().get(0);
  ok(turno15.factible()&&turno15.salida().equals(T.toLocalDate().atTime(15,0)),"no programa turno de las 15:00");
  var noche=T.toLocalDate().atTime(6,30);
  var pedidoNoche=new Pedido("noche",noche.minusHours(1),N,1,4);
  var nocturno=new EstadoOperacion(noche,List.of(pedidoNoche),List.of(new Vehiculo("TA01",N)),e.almacenes(),
    List.of(),List.of(),List.of(),List.of(),Set.of("TA01"));
  var evNoche=new EvaluadorFactibilidad(nocturno,ParametrosOperacion.porDefecto());
  var turno7=evNoche.evaluar(una(evNoche)).rutas().get(0);
  ok(turno7.factible()&&turno7.salida().equals(noche.toLocalDate().atTime(7,0)),"no programa turno de las 07:00");
  var urgente=new Pedido("urgente",noche.minusHours(3),N,1,4);
  var sinMargen=new EstadoOperacion(noche,List.of(urgente),List.of(new Vehiculo("TA01",N)),e.almacenes(),
    List.of(),List.of(),List.of(),List.of(),Set.of("TA01"));
  var evUrgente=new EvaluadorFactibilidad(sinMargen,ParametrosOperacion.porDefecto());
  ok(!evUrgente.evaluar(una(evUrgente)).factible(),"espera siguiente turno aunque vence el plazo");
  pruebas++;
 }
 static void testDescanso(){
  var e=simple(List.of(pedido("p",2)));var ev=new EvaluadorFactibilidad(e,parametros(60));
  var r=ev.evaluar(una(ev)).rutas().get(0);
  ok(r.factible()&&r.descansoInicio()!=null,"sin descanso");
  ok(!r.descansoInicio().isBefore(T.plusHours(1))&&!r.descansoFin().isAfter(T.plusHours(7)),"banda descanso");
  ok(Duration.between(r.descansoInicio(),r.descansoFin()).toMinutes()==60,"duracion descanso");
  for(var p:r.paradas())ok(!GestorDisponibilidad.solapa(p.llegada(),p.finServicio(),r.descansoInicio(),r.descansoFin()),"servicio durante descanso");pruebas++;
 }
 static void testMantenimiento(){
  var e=estado(List.of(pedido("p",1)),List.of(new Vehiculo("TA01",N)),List.of(new Almacen("A",N,10,false)),
   List.of(),List.of(),List.of(new Mantenimiento("TA01",T.plusMinutes(20),T.plusMinutes(40))),List.of(),Set.of());
  var ev=new EvaluadorFactibilidad(e,parametros(60));ok(!ev.evaluar(una(ev)).factible(),"mantenimiento durante ruta admitido");
  ok(!GestorDisponibilidad.solapa(T,T.plusHours(1),T.plusHours(1),T.plusHours(2)),"limite semiabierto");pruebas++;
 }
 static void testAveria(){
  var e=estado(List.of(pedido("p",1)),List.of(new Vehiculo("TA01",N)),List.of(new Almacen("A",N,10,false)),
   List.of(),List.of(new Averia("TA01",T.plusMinutes(20),T.plusMinutes(40))),List.of(),List.of(),Set.of());
  var ev=new EvaluadorFactibilidad(e,parametros(60));ok(!ev.evaluar(una(ev)).factible(),"averia solapada");pruebas++;
 }
 static void testStock(){
  var e=estado(List.of(pedido("p",8)),List.of(new Vehiculo("TA01",N),new Vehiculo("TA02",N)),
   List.of(new Almacen("A",N,4,false)),List.of(),List.of(),List.of(),List.of(),Set.of());
  var ev=new EvaluadorFactibilidad(e,parametros(10));var partes=ev.partes();
  var s=new Solucion(List.of(new Ruta("TA01","A",List.of(partes.get(0)),false),
   new Ruta("TA02","A",List.of(partes.get(1)),false)),List.of());
  ok(!ev.evaluar(s).factible(),"stock agregado");pruebas++;
 }
 static void testBloqueos(){
  var a=new Nodo(1,1);var b=new Nodo(2,1);
  var mapa=new GridMap(List.of(new Bloqueo(T.plusMinutes(1),T.plusMinutes(10),List.of(a,b))));
  var c=new PathFinder(mapa).buscar(a,b,T,40);
  ok(c.distanciaKm()==3,"bloqueo futuro no evitado");
  for(var paso:c.pasos())ok(mapa.cruceValido(paso.origen(),paso.destino(),paso.salida(),paso.llegada()),"arco bloqueado");
  var vuelta=new PathFinder(mapa).buscar(b,a,T.plusMinutes(10),40);
  ok(vuelta.distanciaKm()==1,"retorno no independiente");
  var cierre=new GridMap(List.of(new Bloqueo(T,T.plusMinutes(10),List.of(new Nodo(0,0),new Nodo(1,0))),
    new Bloqueo(T,T.plusMinutes(10),List.of(new Nodo(0,0),new Nodo(0,1)))));
  var espera=new PathFinder(cierre).buscar(new Nodo(0,0),new Nodo(1,0),T,40);
  ok(espera.pasos().get(0).salida().equals(T.plusMinutes(10)),"espera reapertura");pruebas++;
 }
 static void testIntegridad(){
  var ev=new EvaluadorFactibilidad(simple(List.of(pedido("p",1))),parametros(10));var s=una(ev);
  ok(!ev.evaluar(new Solucion(s.rutas(),ev.partes())).factible(),"duplicacion");
  ok(!ev.evaluar(new Solucion(List.of(),List.of())).factible(),"perdida");
  var original=ev.estado();tabu().planificar(original,parametros(10));
  ok(original.almacenes().get(0).stock()==100,"snapshot mutado");pruebas++;
 }
 static void testVecindarios(){
  var e=simple(List.of(pedido("p",1),pedido("q",1),pedido("r",1)));
  var ev=new EvaluadorFactibilidad(e,parametros(10));var s=una(ev);var tipos=new HashSet<TabuMove.Tipo>();
  new RoutingNeighborhood().generar(s,new Random(1),c->{tipos.add(c.movimiento().tipo());ok(c.solucion()!=s,"vecino mutable");return true;});
  ok(tipos.containsAll(List.of(TabuMove.Tipo.SWAP,TabuMove.Tipo.RELOCATE)),"ruteo incompleto");
  var rs=new ArrayList<>(s.rutas());rs.add(new Ruta("TA02","A",List.of(),false));var n=new AtomicInteger();
  new AssignmentNeighborhood().generar(new Solucion(rs,List.of()),e,new Random(1),c->{if(c.movimiento().destino().equals("TA02"))n.incrementAndGet();return true;});
  ok(n.get()>0,"sin transferencias");pruebas++;
 }
 static void testReplanificacion(){
  var p=pedido("p",4);var parte=new PartePedido("p#1",p,4);var activa=new Ruta("TA01","A",List.of(parte),true);
  var e=estado(List.of(p),List.of(new Vehiculo("TA01",N),new Vehiculo("TA02",N)),List.of(new Almacen("A",N,0,false)),
   List.of(),List.of(),List.of(),List.of(activa),Set.of());
  var r=tabu().planificar(e,parametros(10));ok(r.evaluacion().completa()&&r.solucion().rutas().contains(activa),"no conserva carga despachada");
  var averiada=estado(List.of(p),e.vehiculos(),List.of(new Almacen("A",N,10,false)),List.of(),
   List.of(new Averia("TA01",T,T.plusHours(8))),List.of(),List.of(activa),Set.of());
  var auxilio=tabu().planificar(averiada,parametros(10));
  ok(auxilio.evaluacion().completa()&&auxilio.solucion().rutas().stream().anyMatch(x->x.vehiculo().equals("TA02")&&!x.partes().isEmpty()),"no reasigna averia");pruebas++;
 }
 static void testReproducibilidad(){
  var e=simple(List.of(pedido("p",3),pedido("q",5)));
  var inicial=new TabuSearchPlanner(new ConfiguracionTabu(0,3,3,100,0,7)).planificar(e,parametros(10));
  var resultado=tabu().planificar(e,parametros(10));
  ok(resultado.solucion().equals(tabu().planificar(e,parametros(10)).solucion()),"TS no reproducible");
  ok(resultado.evaluacion().objetivo()<=inicial.evaluacion().objetivo(),"empeora inicial");
  ok(resultado.metricas().candidatosEvaluados()>0,"sin candidatos");pruebas++;
 }
 static void testValidacionEntrada(){
  boolean fallo=false;try{simple(List.of(new Pedido("f",T.plusMinutes(1),N,1,4)));}catch(IllegalArgumentException ex){fallo=true;}ok(fallo,"pedido futuro admitido");
  fallo=false;try{new Pedido("x",T,N,1,7);}catch(IllegalArgumentException ex){fallo=true;}ok(fallo,"plazo no permitido");pruebas++;
 }
}
