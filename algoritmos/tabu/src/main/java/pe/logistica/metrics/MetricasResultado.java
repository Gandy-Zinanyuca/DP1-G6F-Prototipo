package pe.logistica.metrics;

import pe.logistica.model.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public record MetricasResultado(RendimientoAlgoritmo rendimiento, boolean factibilidadGlobal,
        double costoTotal, double distanciaTotalKm, double tiempoTotalHoras,
        int pedidosConsiderados, int pedidosAsignados, int pedidosNoAsignados,
        int pedidosDentroDelPlazo, double porcentajeCumplimiento, int vehiculosUtilizados,
        Map<String, Long> capacidadUtilizadaPorVehiculo,
        Map<String, Double> porcentajeCapacidadPorVehiculo,
        double porcentajePromedioUtilizacionCapacidad, double distanciaPromedioPorVehiculo,
        Map<TipoVehiculo, ResumenTipoVehiculo> distribucionPorTipo,
        LocalDateTime fechaHoraPlanificacion, long saMinutos, int k, long scMinutos,
        int maxIteraciones, int tenenciaTabu) {
    public MetricasResultado {
        capacidadUtilizadaPorVehiculo = Collections.unmodifiableMap(new LinkedHashMap<>(capacidadUtilizadaPorVehiculo));
        porcentajeCapacidadPorVehiculo = Collections.unmodifiableMap(new LinkedHashMap<>(porcentajeCapacidadPorVehiculo));
        distribucionPorTipo = Collections.unmodifiableMap(new EnumMap<>(distribucionPorTipo));
    }
    public double taMs() { return rendimiento.taMs(); }
    public static MetricasResultado calcular(EstadoOperacion estado, ConfiguracionTabu config,
            List<Pedido> considerados, EvaluacionSolucion evaluacion, RendimientoAlgoritmo rendimiento) {
        Set<String> ids = considerados.stream().map(Pedido::id).collect(Collectors.toSet());
        Set<String> asignados = new HashSet<>(), aTiempo = new HashSet<>();
        Map<String, Long> cargas = new LinkedHashMap<>();
        Map<String, Double> porcentajes = new LinkedHashMap<>();
        for (Vehiculo v : estado.vehiculos()) { cargas.put(v.codigo(), 0L); porcentajes.put(v.codigo(), 0.0); }
        Map<TipoVehiculo, ResumenTipoVehiculo> tipos = new EnumMap<>(TipoVehiculo.class);
        for (TipoVehiculo tipo : TipoVehiculo.values()) tipos.put(tipo, new ResumenTipoVehiculo(0, 0, 0));
        int usados = 0; double sumaUtilizacion = 0;
        for (ResultadoRuta resultado : evaluacion.rutas()) {
            Ruta ruta = resultado.ruta(); Vehiculo v = ruta.vehiculo();
            long carga = ruta.carga();
            double porcentaje = 100.0 * carga / v.tipo().capacidad();
            cargas.put(v.codigo(), carga); porcentajes.put(v.codigo(), porcentaje);
            for (Entrega entrega : ruta.entregas()) if (ids.contains(entrega.pedido().id())) asignados.add(entrega.pedido().id());
            // Una ruta con incumplimiento duro no cuenta como servicio entregable.
            if (resultado.factible()) for (Visita visita : resultado.visitas())
                if (visita.dentroDelPlazo() && ids.contains(visita.pedido().id())) aTiempo.add(visita.pedido().id());
            if (ruta.entregas().isEmpty()) continue;
            usados++; sumaUtilizacion += porcentaje;
            ResumenTipoVehiculo anterior = tipos.get(v.tipo());
            tipos.put(v.tipo(), new ResumenTipoVehiculo(anterior.utilizados() + 1,
                    anterior.distanciaKm() + resultado.distanciaKm(), anterior.costo() + resultado.costo()));
        }
        int total = considerados.size();
        return new MetricasResultado(rendimiento, evaluacion.factible(), evaluacion.costoTotal(),
                evaluacion.distanciaTotalKm(), evaluacion.tiempoTotalHoras(), total, asignados.size(),
                total - asignados.size(), aTiempo.size(), total == 0 ? 100 : 100.0 * aTiempo.size() / total,
                usados, cargas, porcentajes, usados == 0 ? 0 : sumaUtilizacion / usados,
                usados == 0 ? 0 : evaluacion.distanciaTotalKm() / usados, tipos,
                estado.instantePlanificacion(), config.saMinutos(), config.k(), config.scMinutos(),
                config.maxIteraciones(), config.tenenciaTabu());
    }
}
