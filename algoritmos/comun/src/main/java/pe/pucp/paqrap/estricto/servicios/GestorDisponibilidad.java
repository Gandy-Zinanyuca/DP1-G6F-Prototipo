package pe.pucp.paqrap.estricto.servicios;
import pe.pucp.paqrap.estricto.modelo.*;
import java.time.*;
public final class GestorDisponibilidad {
 public static boolean solapa(LocalDateTime a,LocalDateTime b,LocalDateTime c,LocalDateTime d) { return a.isBefore(d)&&b.isAfter(c); }
 public static boolean disponible(EstadoOperacion e,Vehiculo v,LocalDateTime inicio,LocalDateTime fin) {
  return v.disponible() && !inicio.isBefore(v.disponibleDesde()) &&
   e.averias().stream().noneMatch(a->a.vehiculo().equals(v.codigo())&&solapa(inicio,fin,a.inicio(),a.fin())) &&
   e.mantenimientos().stream().noneMatch(m->m.vehiculo().equals(v.codigo())&&solapa(inicio,fin,m.inicio(),m.fin()));
 }
 private GestorDisponibilidad() {}
}
