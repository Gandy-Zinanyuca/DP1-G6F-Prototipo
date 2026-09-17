package pe.pucp.paqrap.estricto.modelo;
import pe.pucp.paqrap.estricto.caminos.Camino;
import java.time.*;
import java.util.*;
public record ResultadoRuta(Ruta ruta, LocalDateTime salida, LocalDateTime fin, String almacenRetorno,
 List<Parada> paradas, List<Camino> caminos, LocalDateTime descansoInicio, LocalDateTime descansoFin,
 double distanciaKm, double costo, List<String> errores) {
 public ResultadoRuta { paradas=List.copyOf(paradas); caminos=List.copyOf(caminos); errores=List.copyOf(errores); }
 public boolean factible() { return errores.isEmpty(); }
 public double minutos() { return Duration.between(salida,fin).toNanos()/60_000_000_000.0; }
}
