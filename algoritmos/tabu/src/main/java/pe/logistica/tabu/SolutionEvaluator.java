package pe.logistica.tabu;

import pe.logistica.model.*;
import pe.logistica.routing.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Unico punto de validacion para ambos vecindarios y la construccion inicial. */
public final class SolutionEvaluator {
    private final EstadoOperacion estado;
    private final Map<String, Pedido> requeridos;
    private final Map<String, Vehiculo> flota;
    private final PathFinder caminos;
    // Cache acotada y local a una ejecucion: el calendario y T no cambian.
    private final Map<Ruta, ResultadoRuta> cache = new LinkedHashMap<>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;
        @Override protected boolean removeEldestEntry(Map.Entry<Ruta, ResultadoRuta> entrada) { return size() > 20_000; }
    };
    public SolutionEvaluator(EstadoOperacion estado, List<Pedido> considerados) {
        this.estado = estado;
        requeridos = considerados.stream().collect(Collectors.toMap(Pedido::id, Function.identity()));
        flota = estado.vehiculos().stream().collect(Collectors.toMap(Vehiculo::codigo, Function.identity()));
        caminos = new PathFinder(new GridMap(estado.bloqueos(), estado.parametros().bloquearNodos()));
    }
    public EvaluacionSolucion evaluar(Solucion solucion) { return evaluar(solucion, true); }
    /** exigirTodos=false solo se permite durante la insercion constructiva inicial. */
    public EvaluacionSolucion evaluar(Solucion solucion, boolean exigirTodos) {
        var errores = new ArrayList<String>(); var resultados = new ArrayList<ResultadoRuta>();
        var vehiculosVistos = new HashSet<String>();
        var entregadoPorPedido = new HashMap<String, Long>();
        for (Ruta ruta : solucion.rutas()) {
            if (!ruta.vehiculo().equals(flota.get(ruta.vehiculo().codigo())))
                errores.add("Vehiculo desconocido o alterado: " + ruta.vehiculo().codigo());
            if (!vehiculosVistos.add(ruta.vehiculo().codigo()))
                errores.add("Vehiculo con mas de una ruta: " + ruta.vehiculo().codigo());
            for (Entrega e : ruta.entregas()) {
                Pedido p = e.pedido();
                if (!p.equals(requeridos.get(p.id()))) errores.add("Pedido desconocido o alterado: " + p.id());
                entregadoPorPedido.merge(p.id(), (long) e.cantidad(), Long::sum);
            }
            ResultadoRuta resultado = cache.computeIfAbsent(ruta, this::evaluarRuta);
            resultados.add(resultado); errores.addAll(resultado.incumplimientos());
        }
        // Una entrega puede repartirse entre varias rutas; lo que no puede pasar es excederse.
        for (var entrada : entregadoPorPedido.entrySet()) {
            Pedido p = requeridos.get(entrada.getKey());
            if (p != null && entrada.getValue() > p.cantidad())
                errores.add("Entrega excede cantidad del pedido: " + entrada.getKey());
        }
        if (exigirTodos) requeridos.keySet().stream().sorted().forEach(id -> {
            long entregado = entregadoPorPedido.getOrDefault(id, 0L);
            long requerido = requeridos.get(id).cantidad();
            if (entregado == 0) errores.add("Pedido no asignado: " + id);
            else if (entregado < requerido) errores.add("Pedido incompleto: " + id + " (entregado " + entregado + " de " + requerido + ")");
        });
        return new EvaluacionSolucion(resultados, errores);
    }
    private ResultadoRuta evaluarRuta(Ruta ruta) {
        Vehiculo v = ruta.vehiculo(); LocalDateTime t = estado.instantePlanificacion();
        if (ruta.entregas().isEmpty()) return new ResultadoRuta(ruta, t, t, List.of(), List.of(), 0, 0, 0, List.of());
        var errores = new ArrayList<String>();
        if (!v.disponible()) errores.add(v.codigo() + ": vehiculo no disponible");
        if (ruta.carga() > v.tipo().capacidad()) errores.add(v.codigo() + ": capacidad excedida");
        // Todos los bultos se cargan antes de salir; un pedido futuro no puede viajar antes de existir.
        LocalDateTime salida = v.disponibleDesde().isAfter(t) ? v.disponibleDesde() : t;
        for (Entrega e : ruta.entregas()) if (e.pedido().fechaRegistro().isAfter(salida)) salida = e.pedido().fechaRegistro();
        if (enMantenimiento(v, salida, salida.plusNanos(1))) errores.add(v.codigo() + ": mantenimiento al salir");
        if (!errores.isEmpty()) return new ResultadoRuta(ruta, salida, salida, List.of(), List.of(), 0, 0, 0, errores);
        var visitas = new ArrayList<Visita>(); var trayectos = new ArrayList<Camino>();
        Nodo actual = v.ubicacionInicial(); LocalDateTime hora = salida;
        double distancia = 0;
        for (Entrega e : ruta.entregas()) {
            Pedido p = e.pedido();
            Camino camino = caminos.buscarOpcional(actual, p.ubicacion(), hora, estado.parametros().velocidad(v.tipo()));
            // Pseudocodigo 5.1: si no existe camino, se registra el desplazamiento imposible y se continua.
            if (camino == null) { errores.add(p.id() + ": desplazamiento imposible"); continue; }
            trayectos.add(camino); distancia += camino.distanciaKm();
            LocalDateTime inicio = camino.llegada();
            if (p.fechaRegistro().isAfter(inicio)) inicio = p.fechaRegistro();
            Visita visita = new Visita(e, camino.llegada(), inicio, inicio.plusMinutes(estado.parametros().servicioMinutos()),
                    estado.parametros().plazoIncluyeServicio());
            visitas.add(visita);
            if (!visita.dentroDelPlazo()) errores.add(p.id() + ": " + (estado.parametros().plazoIncluyeServicio()
                    ? "fin de servicio" : "llegada") + " fuera del plazo");
            hora = visita.finAtencion(); actual = p.ubicacion();
        }
        Camino regreso = caminos.buscarOpcional(actual, v.ubicacionInicial(), hora, estado.parametros().velocidad(v.tipo()));
        if (regreso == null) {
            errores.add(v.codigo() + ": regreso al deposito imposible");
        } else {
            trayectos.add(regreso); distancia += regreso.distanciaKm(); hora = regreso.llegada();
        }
        if (enMantenimiento(v, salida, hora)) errores.add(v.codigo() + ": ruta coincide con mantenimiento");
        double horas = Duration.between(salida, hora).toNanos() / 3_600_000_000_000.0;
        return new ResultadoRuta(ruta, salida, hora, visitas, trayectos, distancia, horas,
                distancia * v.tipo().costoPorKm(), errores);
    }
    private boolean enMantenimiento(Vehiculo v, LocalDateTime inicio, LocalDateTime fin) {
        return estado.mantenimientos().stream().anyMatch(m -> m.codigoVehiculo().equals(v.codigo())
                && inicio.isBefore(m.fecha().plusDays(1).atStartOfDay())
                && fin.isAfter(m.fecha().atStartOfDay()));
    }
}
