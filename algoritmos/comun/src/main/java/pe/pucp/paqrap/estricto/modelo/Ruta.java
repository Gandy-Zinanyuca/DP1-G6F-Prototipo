package pe.pucp.paqrap.estricto.modelo;
import java.util.*;
/** Una salida por vehiculo/ciclo. La bandera enCurso conserva carga ya despachada. */
public record Ruta(String vehiculo, String almacenOrigen, List<PartePedido> partes, boolean enCurso) {
 public Ruta { Objects.requireNonNull(vehiculo); Objects.requireNonNull(almacenOrigen); partes=List.copyOf(partes); }
 public Ruta conPartes(List<PartePedido> nuevas) { return new Ruta(vehiculo,almacenOrigen,nuevas,enCurso); }
 public int carga() { return Math.toIntExact(partes.stream().mapToLong(PartePedido::cantidad).sum()); }
}
