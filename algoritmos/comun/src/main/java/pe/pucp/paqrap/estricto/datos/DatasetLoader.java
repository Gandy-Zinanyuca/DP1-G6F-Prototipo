package pe.pucp.paqrap.estricto.datos;
import pe.pucp.paqrap.estricto.modelo.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.util.regex.Pattern;
/** Adaptador de archivos. No aplica Sa/Sc/K ni dispara ciclos. Seleccion del snapshot explicita. */
public final class DatasetLoader {
 public record Carga(EstadoOperacion estado,int pedidosLeidos,int futurosExcluidos,int fueraHorizonte,
                     int fueraLimite,int bloqueosLeidos) {}
 public Carga cargar(Path ventas,Path bloqueos,Path mantenimiento,LocalDateTime instante,
   int horizonteHoras,int maxPedidos) throws IOException {
  if(horizonteHoras<=0 || maxPedidos<=0)throw new IllegalArgumentException("Horizonte y limite positivos");
  var m=Pattern.compile("ventas\\.(\\d{4})(\\d{2})\\.txt").matcher(ventas.getFileName().toString());
  if(!m.matches())throw new IllegalArgumentException("Nombre ventas.AAAAMM.txt requerido");
  int anio=Integer.parseInt(m.group(1)),mes=Integer.parseInt(m.group(2));
  if(!YearMonth.of(anio,mes).equals(YearMonth.from(instante)))throw new IllegalArgumentException("Instante fuera del mes de ventas");
  if(!bloqueos.getFileName().toString().equals(String.format(Locale.ROOT,"bloqueo.%02d%02d.txt",anio%100,mes)))throw new IllegalArgumentException("Bloqueos de otro mes");
  var pedidos=new PedidoParser().leer(ventas,anio,mes);var cierres=new BloqueoParser().leer(bloqueos,anio,mes);
  var mant=ParserSupport.leer(mantenimiento,linea->{
   String[] campos=linea.split(":",-1);
   if(campos.length!=2)throw new IllegalArgumentException("Mantenimiento AAAAMMDD:unidad");
   LocalDate fecha=LocalDate.parse(campos[0].trim(),DateTimeFormatter.BASIC_ISO_DATE);
   return new Mantenimiento(campos[1].trim(),fecha.atStartOfDay(),fecha.plusDays(1).atStartOfDay());
  });
  var seleccion=new ArrayList<Pedido>();int futuros=0,fuera=0;
  for(var p:pedidos) {
   if(p.fechaRegistro().isAfter(instante)){futuros++;continue;}
   if(p.deadline().isAfter(instante.plusHours(horizonteHoras))){fuera++;continue;}
   seleccion.add(p);
  }
  seleccion.sort(Comparator.comparing(Pedido::deadline).thenComparing(Pedido::id));
  int limite=Math.max(0,seleccion.size()-maxPedidos);
  seleccion=new ArrayList<>(seleccion.subList(0,Math.min(maxPedidos,seleccion.size())));
  // Se conservan las coordenadas y velocidades de la linea base ALNS del repositorio.
  var almacenes=List.of(new Almacen("CENTRAL",new Nodo(25,15),0,true),
    new Almacen("NOROESTE",new Nodo(12,38),1000,false),new Almacen("ESTE",new Nodo(55,27),1000,false));
  var flota=new ArrayList<Vehiculo>();
  for(var tipo:TipoVehiculo.values())for(int n=1;n<=(tipo==TipoVehiculo.TA?10:tipo==TipoVehiculo.TM?15:12);n++)
   flota.add(new Vehiculo(String.format(Locale.ROOT,"%s%02d",tipo,n),almacenes.get(0).nodo()));
  return new Carga(new EstadoOperacion(instante,seleccion,flota,almacenes,cierres,List.of(),mant,List.of(),Set.of()),
    pedidos.size(),futuros,fuera,limite,cierres.size());
 }
}
