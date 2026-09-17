package pe.pucp.paqrap.estricto.modelo;
import java.time.LocalDateTime;
public record Mantenimiento(String vehiculo, LocalDateTime inicio, LocalDateTime fin) {
 public Mantenimiento { if(vehiculo==null || inicio==null || fin==null || !inicio.isBefore(fin)) throw new IllegalArgumentException("Mantenimiento invalido"); }
}
