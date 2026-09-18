package pe.pucp.paqrap.estricto.modelo;
import java.time.LocalDateTime;
public record Averia(String vehiculo, LocalDateTime inicio, LocalDateTime fin) {
 public Averia { 
        if(vehiculo==null || inicio==null || fin==null || !inicio.isBefore(fin)) throw new IllegalArgumentException("Averia invalida"); 
    }
}
