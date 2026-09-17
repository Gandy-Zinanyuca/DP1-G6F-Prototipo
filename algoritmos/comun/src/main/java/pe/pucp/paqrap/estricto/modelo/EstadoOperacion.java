package pe.pucp.paqrap.estricto.modelo;
import java.time.LocalDateTime;
import java.util.*;
/** Snapshot inmutable. Pedidos y cantidades representan exclusivamente lo aun pendiente de entregar. */
public record EstadoOperacion(LocalDateTime instante, List<Pedido> pedidos, List<Vehiculo> vehiculos,
 List<Almacen> almacenes, List<Bloqueo> bloqueos, List<Averia> averias,
 List<Mantenimiento> mantenimientos, List<Ruta> rutasEnCurso, Set<String> descansoRealizado) {
 public EstadoOperacion {
  Objects.requireNonNull(instante);
  pedidos=List.copyOf(pedidos); vehiculos=List.copyOf(vehiculos); almacenes=List.copyOf(almacenes);
  bloqueos=List.copyOf(bloqueos); averias=List.copyOf(averias); mantenimientos=List.copyOf(mantenimientos);
  rutasEnCurso=List.copyOf(rutasEnCurso); descansoRealizado=Set.copyOf(descansoRealizado);
  unicos(pedidos.stream().map(Pedido::id).toList()); unicos(vehiculos.stream().map(Vehiculo::codigo).toList());
  unicos(almacenes.stream().map(Almacen::id).toList()); unicos(rutasEnCurso.stream().map(Ruta::vehiculo).toList());
  if(almacenes.isEmpty()) throw new IllegalArgumentException("Sin almacenes");
  var vs=new HashSet<>(vehiculos.stream().map(Vehiculo::codigo).toList());
  for(var a:averias) if(!vs.contains(a.vehiculo())) throw new IllegalArgumentException("Averia de unidad desconocida");
  for(var m:mantenimientos) if(!vs.contains(m.vehiculo())) throw new IllegalArgumentException("Mantenimiento de unidad desconocida");
  if(!vs.containsAll(descansoRealizado)) throw new IllegalArgumentException("Descanso de unidad desconocida");
  for(var r:rutasEnCurso) if(!r.enCurso() || !vs.contains(r.vehiculo()) ||
    almacenes.stream().noneMatch(a->a.id().equals(r.almacenOrigen()))) throw new IllegalArgumentException("Ruta en curso invalida");
  for(var p:pedidos) if(p.fechaRegistro().isAfter(instante)) throw new IllegalArgumentException("El backend debe excluir pedidos futuros");
 }
 private static void unicos(List<String> ids) { if(new HashSet<>(ids).size()!=ids.size()) throw new IllegalArgumentException("Identificador duplicado"); }
}
