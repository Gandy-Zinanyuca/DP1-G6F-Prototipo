package pe.logistica;

import pe.logistica.model.*;
import pe.logistica.metrics.*;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ConsoleReport {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private ConsoleReport() { }
    public static void imprimir(ResultadoPlanificacion resultado, PrintStream out, boolean detalleCaminos) {
        MetricasResultado m = resultado.metricas(); RendimientoAlgoritmo r = m.rendimiento();
        out.println("========== TABU SEARCH ==========\n");
        out.println("Estado: " + (m.factibilidadGlobal() ? "FACTIBLE" : "NO FACTIBLE"));
        out.println("Tiempo simulado: " + m.fechaHoraPlanificacion().format(FECHA));
        out.printf("Sa: %d min%nK: %d%nSc: %d min%n", m.saMinutos(), m.k(), m.scMinutos());
        out.println("Ventana de pedidos: [" + m.fechaHoraPlanificacion().format(FECHA) + ", "
                + m.fechaHoraPlanificacion().plusMinutes(m.scMinutos()).format(FECHA) + "]");
        out.printf("Maximo de iteraciones: %d%nTenencia tabu: %d%n", m.maxIteraciones(), m.tenenciaTabu());
        var parametros = resultado.parametros();
        out.println("Deadline evaluado en: " + (parametros.plazoIncluyeServicio() ? "FIN_SERVICIO" : "LLEGADA"));
        out.println("Servicio: " + parametros.servicioMinutos() + " min; bloqueo: " + (parametros.bloquearNodos() ? "NODOS (calles incidentes)" : "CALLES"));
        for (TipoVehiculo tipo : TipoVehiculo.values()) out.printf(Locale.ROOT, "Velocidad %s: %.3f km/h%n", tipo, parametros.velocidad(tipo));
        out.println("\n--- RUTAS ---");
        for (ResultadoRuta rr : resultado.evaluacion().rutas()) {
            Ruta ruta = rr.ruta(); Vehiculo v = ruta.vehiculo();
            out.printf("%n%s | %s (%s)%n", v.codigo(), v.tipo(), v.tipo().descripcion());
            if (ruta.entregas().isEmpty()) {
                out.println("Sin pedidos | Distancia: 0 km | Tiempo: 0 h | Costo: S/ 0.00");
                out.printf("Capacidad: 0/%d (0.00 %%)%n", v.tipo().capacidad());
                continue;
            }
            out.println("Orden: " + ruta.entregas().stream().map(e -> e.pedido().id()).collect(Collectors.joining(" -> ")));
            var coordenadas = new ArrayList<String>(); coordenadas.add(v.ubicacionInicial().toString());
            ruta.entregas().forEach(e -> coordenadas.add(e.pedido().ubicacion().toString())); coordenadas.add(v.ubicacionInicial().toString());
            out.println("Coordenadas de visita (con regreso): " + String.join(" -> ", coordenadas));
            out.println("Salida: " + rr.salida().format(FECHA) + " | Regreso: " + rr.fin().format(FECHA));
            out.printf(Locale.ROOT, "Distancia: %.2f km%nTiempo estimado: %.4f h%nCosto: S/ %.2f%n",
                    rr.distanciaKm(), rr.tiempoHoras(), rr.costo());
            out.printf(Locale.ROOT, "Capacidad: %d/%d (%.2f %%)%n", m.capacidadUtilizadaPorVehiculo().get(v.codigo()),
                    v.tipo().capacidad(), m.porcentajeCapacidadPorVehiculo().get(v.codigo()));
            for (Visita visita : rr.visitas()) out.printf("  %s cliente=%s %s | cantidad=%d | llegada=%s | atencion=%s a %s | deadline=%s | %s%n",
                    visita.pedido().id(), visita.pedido().clienteId(), visita.pedido().ubicacion(), visita.entrega().cantidad(), visita.llegada().format(FECHA),
                    visita.inicioAtencion().format(FECHA), visita.finAtencion().format(FECHA),
                    visita.pedido().deadline().format(FECHA), visita.dentroDelPlazo() ? "A TIEMPO" : "FUERA DE PLAZO");
            if (detalleCaminos) for (var camino : rr.caminos()) {
                out.println("  Camino: " + camino.coordenadas().stream().map(Object::toString).collect(Collectors.joining(" -> ")));
                for (var paso : camino.pasos()) out.printf("    %s -> %s | %s a %s%n", paso.origen(), paso.destino(),
                        paso.salida().format(FECHA), paso.llegada().format(FECHA));
            }
        }
        out.println("\n--- METRICAS ---");
        out.printf(Locale.ROOT, "Ta: %.3f ms%n", m.taMs());
        out.printf("Iteraciones: %d%nCandidatos evaluados: %d%nCandidatos factibles: %d%nMovimientos tabu rechazados: %d%nAspiraciones aplicadas: %d%nMejor solucion encontrada en iteracion: %d%n",
                r.iteraciones(), r.candidatosEvaluados(), r.solucionesFactiblesEvaluadas(), r.movimientosTabuRechazados(),
                r.aspiracionesAplicadas(), r.iteracionMejorSolucion());
        out.printf("Inserciones iniciales evaluadas: %d%nMotivo de parada: %s%n", r.insercionesInicialesEvaluadas(), r.motivoParada());
        out.printf(Locale.ROOT, "Costo de solucion inicial%s: S/ %.2f%n", m.factibilidadGlobal() ? "" : " parcial", r.costoSolucionInicial());
        out.printf("%nPedidos considerados: %d%nPedidos asignados: %d%nPedidos no asignados: %d%nPedidos dentro del plazo: %d%n",
                m.pedidosConsiderados(), m.pedidosAsignados(), m.pedidosNoAsignados(), m.pedidosDentroDelPlazo());
        out.printf(Locale.ROOT, "Cumplimiento: %.2f %%%n%nVehiculos utilizados: %d%nDistancia total: %.2f km%nTiempo total estimado de rutas: %.4f h%nCosto total: S/ %.2f%nUtilizacion promedio de capacidad (vehiculos utilizados): %.2f %%%nDistancia promedio por vehiculo utilizado: %.2f km%n",
                m.porcentajeCumplimiento(), m.vehiculosUtilizados(), m.distanciaTotalKm(), m.tiempoTotalHoras(), m.costoTotal(),
                m.porcentajePromedioUtilizacionCapacidad(), m.distanciaPromedioPorVehiculo());
        out.println("\n--- DISTRIBUCION POR TIPO ---");
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            ResumenTipoVehiculo resumen = m.distribucionPorTipo().get(tipo);
            out.printf(Locale.ROOT, "%s (%s): %d utilizados | %.2f km | S/ %.2f%n", tipo, tipo.descripcion(),
                    resumen.utilizados(), resumen.distanciaKm(), resumen.costo());
        }
        if (!resultado.evaluacion().factible()) {
            out.println("\n--- INCUMPLIMIENTOS (plan parcial no ejecutable) ---");
            resultado.evaluacion().incumplimientos().forEach(error -> out.println("- " + error));
        }
    }
}
