package pe.logistica.model;

import java.util.EnumMap;
import java.util.Map;

/** Instantanea de parametros: los cambios se aplican en la siguiente ejecucion. */
public record ParametrosOperacion(Map<TipoVehiculo, Double> velocidadesKmh,
                                  int servicioMinutos, boolean plazoIncluyeServicio,
                                  boolean bloquearNodos) {
    public ParametrosOperacion {
        var copia = new EnumMap<TipoVehiculo, Double>(TipoVehiculo.class);
        copia.putAll(velocidadesKmh);
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            Double velocidad = copia.get(tipo);
            if (velocidad == null || !Double.isFinite(velocidad) || velocidad < 1 || velocidad > 300)
                throw new IllegalArgumentException("Velocidad fuera de [1,300] km/h: " + tipo);
        }
        if (servicioMinutos <= 0) throw new IllegalArgumentException("Servicio debe ser positivo");
        velocidadesKmh = Map.copyOf(copia);
    }
    public double velocidad(TipoVehiculo tipo) { return velocidadesKmh.get(tipo); }
    public static ParametrosOperacion publicados() {
        return new ParametrosOperacion(Map.of(TipoVehiculo.TA, 20.0, TipoVehiculo.TM, 40.0, TipoVehiculo.TB, 14.0), 60, false, true);
    }
    public static ParametrosOperacion legado() {
        return new ParametrosOperacion(Map.of(TipoVehiculo.TA, 40.0, TipoVehiculo.TM, 25.0, TipoVehiculo.TB, 12.0), 60, true, false);
    }
}
