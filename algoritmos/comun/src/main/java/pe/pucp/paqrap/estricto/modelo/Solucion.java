package pe.pucp.paqrap.estricto.modelo;
import java.util.*;
public record Solucion(List<Ruta> rutas, List<PartePedido> pendientes) {
 public Solucion { rutas=List.copyOf(rutas); pendientes=List.copyOf(pendientes); }
 public Solucion reemplazar(int indice, Ruta ruta) { var rs=new ArrayList<>(rutas); rs.set(indice,ruta); return new Solucion(rs,pendientes); }
}
