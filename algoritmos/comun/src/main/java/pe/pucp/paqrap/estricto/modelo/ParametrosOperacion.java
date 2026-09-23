package pe.pucp.paqrap.estricto.modelo;
import java.util.*;
/** Parametros compartidos. descansoDesde/Hasta delimitan el INICIO de la alimentacion, relativos al turno. */
public record ParametrosOperacion(int servicioMinutos, boolean plazoIncluyeServicio,
 int turnoMinutos, int inicioTurnoMinuto, int descansoDesde, int descansoHasta, int descansoMinutos,
 int tamanioParte, double costoFijoVehiculo, double penalizacionPaquetePendiente,
 Map<TipoVehiculo,Double> velocidades) {
 public ParametrosOperacion {
  velocidades=Map.copyOf(velocidades);
  if(servicioMinutos<0 || turnoMinutos<=0 || 1440%turnoMinutos!=0 || inicioTurnoMinuto<0 || inicioTurnoMinuto>=1440 || descansoDesde<0 ||
     descansoMinutos<=0 || descansoDesde>descansoHasta || descansoHasta>turnoMinutos-descansoMinutos ||
     tamanioParte<=0 || !Double.isFinite(costoFijoVehiculo) || costoFijoVehiculo<0 ||
     !Double.isFinite(penalizacionPaquetePendiente) || penalizacionPaquetePendiente<=0) throw new IllegalArgumentException("Parametros invalidos");
  for(var tipo:TipoVehiculo.values()) if(!velocidades.containsKey(tipo) || !Double.isFinite(velocidades.get(tipo)) ||
    velocidades.get(tipo)<1 || velocidades.get(tipo)>300) throw new IllegalArgumentException("Velocidad invalida");
 }
 public static ParametrosOperacion porDefecto() {
  return new ParametrosOperacion(60,true,480,420,60,420,60,4,50,1_000_000,
    Map.of(TipoVehiculo.TA,40.0,TipoVehiculo.TM,25.0,TipoVehiculo.TB,12.0));
 }
}
