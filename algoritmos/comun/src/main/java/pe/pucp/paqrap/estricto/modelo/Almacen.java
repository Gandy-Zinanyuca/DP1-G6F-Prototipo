package pe.pucp.paqrap.estricto.modelo;
import java.util.Objects;
public record Almacen(String id, Nodo nodo, int stock, boolean ilimitado) {
 public Almacen { Objects.requireNonNull(id); Objects.requireNonNull(nodo); if(id.isBlank() || stock<0) throw new IllegalArgumentException("Almacen invalido"); }
}
