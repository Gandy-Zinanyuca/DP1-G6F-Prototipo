package pe.pucp.paqrap;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import pe.pucp.paqrap.estricto.datos.DatasetLoader;
import pe.pucp.paqrap.estricto.modelo.*;

/** Preparacion externa a los motores; opciones despues de los argumentos posicionales. */
final class EntradaExperimento {
 final String[] args;
 final Map<String,String> opciones=new LinkedHashMap<>();
 final DatasetLoader.Carga carga;
 final EstadoOperacion estado;
 final String escenario,nivel,instancia;
 final double factor;
 final int maxPedidos,horizonte;
 EntradaExperimento(String[] entrada) throws Exception {
  int corte=0;
  while(corte<entrada.length && !entrada[corte].startsWith("--")) corte++;
  args=Arrays.copyOf(entrada,corte);
  if(args.length<4) throw new IllegalArgumentException("Se requieren ventas, bloqueos, mantenimiento e instante ISO");
  var permitidas=Set.of("escenario","carga","instancia","factor-carga","max-pedidos","horizonte-horas","averias","salida");
  for(int i=corte;i<entrada.length;i+=2) {
   if(i+1>=entrada.length || !entrada[i].startsWith("--")) throw new IllegalArgumentException("Opcion sin valor");
   String clave=entrada[i].substring(2);
   if(!permitidas.contains(clave) || opciones.putIfAbsent(clave,entrada[i+1])!=null)
    throw new IllegalArgumentException("Opcion desconocida o repetida: "+clave);
  }
  escenario=opciones.getOrDefault("escenario","E1");
  if(!Set.of("E1","E2","E3").contains(escenario)) throw new IllegalArgumentException("Escenario E1, E2 o E3");
  factor=Double.parseDouble(opciones.getOrDefault("factor-carga","1"));
  if(!Double.isFinite(factor) || factor<=0) throw new IllegalArgumentException("Factor positivo");
  nivel=opciones.getOrDefault("carga",Double.toString(factor));
  instancia=opciones.getOrDefault("instancia",Path.of(args[0]).getFileName()+"@"+args[3]);
  maxPedidos=Integer.parseInt(opciones.getOrDefault("max-pedidos","400"));
  horizonte=Integer.parseInt(opciones.getOrDefault("horizonte-horas","24"));
  carga=new DatasetLoader().cargar(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),LocalDateTime.parse(args[3]),horizonte,maxPedidos);
  var e=carga.estado();
  var pedidos=new ArrayList<Pedido>();
  for(var p:e.pedidos()) {
   double cantidad=Math.ceil(p.cantidad()*factor);
   if(cantidad>Integer.MAX_VALUE) throw new IllegalArgumentException("Carga fuera de rango");
   pedidos.add(new Pedido(p.id(),p.fechaRegistro(),p.ubicacion(),(int)cantidad,p.plazoHoras(),p.clienteId()));
  }
  var averias=new ArrayList<Averia>();
  if(opciones.containsKey("averias")) for(String linea:Files.readAllLines(Path.of(opciones.get("averias")))) {
   if(linea.isBlank() || linea.startsWith("#")) continue;
   String[] c=linea.split(";",-1);
   if(c.length!=3) throw new IllegalArgumentException("Averia: vehiculo;inicio-ISO;fin-ISO");
   averias.add(new Averia(c[0].trim(),LocalDateTime.parse(c[1].trim()),LocalDateTime.parse(c[2].trim())));
  }
  estado=new EstadoOperacion(e.instante(),pedidos,e.vehiculos(),e.almacenes(),e.bloqueos(),averias,e.mantenimientos(),e.rutasEnCurso(),e.descansoRealizado());
 }
 int iteraciones(int pos,int defecto) {
  int n=args.length>pos?Integer.parseInt(args[pos]):defecto;
  if(n<0) throw new IllegalArgumentException("Iteraciones negativas");
  return n;
 }
 long presupuesto(int pos) {
  long n=args.length>pos?Long.parseLong(args[pos]):0;
  if(n<0) throw new IllegalArgumentException("Presupuesto negativo");
  return n;
 }
}
