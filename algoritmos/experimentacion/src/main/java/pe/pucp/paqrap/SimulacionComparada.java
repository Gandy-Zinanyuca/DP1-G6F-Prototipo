package pe.pucp.paqrap;

import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;
import pe.pucp.paqrap.estricto.datos.*;
import pe.pucp.paqrap.tabu.*;
import pe.pucp.paqrap.alns.estricto.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.io.*;

/**
 * Reloj externo a los motores: ingresos, despachos, entregas y retornos
 * persistentes. Cada algoritmo/semilla recibe una simulacion nueva de la misma
 * demanda. Un plan incompleto detiene la corrida; no prueba inviabilidad
 * matematica.
 */
public final class SimulacionComparada {
    public record Datos(EstadoOperacion base, List<Pedido> pedidos) {
        public Datos {
            pedidos = List.copyOf(pedidos);
        }
    }

    public record Resumen(String fin, LocalDateTime instante, int ciclos, int completos, int paquetesEntregados,
            double holguraMedia, double holguraMinima, int ejecuciones, double taTotalMs, double taMaxMs,
            double distanciaKm, double minutosRutas, int vehiculos, double utilizacion) {
    }

    private record Entrega(PartePedido parte, LocalDateTime fin) {
    }

    /**
     * Acredita solo una hora continua ya transcurrida sin ruta, dentro de la banda.
     * No supone descanso anterior al arranque ni suma intervalos interrumpidos. El
     * mapa contiene el FIN efectivo del descanso (tambien para rutas
     * comprometidas).
     */
    static Set<String> descansosCompletados(EstadoOperacion base, List<Vehiculo> flota, LocalDateTime ahora,
            ParametrosOperacion p, Map<String, LocalDateTime> finales) {
        var inicioTurno = turno(ahora, p);
        var hechos = new HashSet<String>();
        for (var v : flota) {
            var fin = finales.get(v.codigo());
            if (fin != null && !fin.isAfter(ahora) && fin.isAfter(inicioTurno)) {
                hechos.add(v.codigo());
                continue;
            }
            if (!v.disponible())
                continue;
            var inicio = inicioTurno.plusMinutes(p.descansoDesde());
            if (base.instante().isAfter(inicio))
                inicio = base.instante();
            if (v.disponibleDesde().isAfter(inicio))
                inicio = v.disponibleDesde();
            var termino = inicio.plusMinutes(p.descansoMinutos());
            if (!inicio.isAfter(inicioTurno.plusMinutes(p.descansoHasta())) && !termino.isAfter(ahora)) {
                finales.put(v.codigo(), termino);
                hechos.add(v.codigo());
            }
        }
        return hechos;
    }

    private static LocalDateTime turno(LocalDateTime t, ParametrosOperacion p) {
        int m = t.getHour() * 60 + t.getMinute();
        return t.toLocalDate().atStartOfDay().plusMinutes(
                p.inicioTurnoMinuto() + Math.floorDiv(m - p.inicioTurnoMinuto(), p.turnoMinutos()) * p.turnoMinutos());
    }

    /**
     * Mantiene cantidades por pedido original; una entrega solo completa al recibir
     * la ultima parte.
     */
    public static Resumen ejecutar(Datos datos, PlanificadorEstricto motor, int sa, int maxCiclos, Path archivo,
            long semilla, double factor) throws IOException {
        if (sa <= 0 || maxCiclos < 0 || !Double.isFinite(factor) || factor <= 0)
            throw new IllegalArgumentException("Sa positivo, ciclos >= 0 y factor positivo requeridos");
        var base = datos.base();
        if (!base.averias().isEmpty() || !base.mantenimientos().isEmpty())
            throw new IllegalArgumentException(
                    "La experimentacion excluye averias y mantenimiento; use escenarios operativos manuales para esas incidencias.");
        var par = ParametrosOperacion.porDefecto();
        var flota = new ArrayList<>(base.vehiculos());
        var almacenes = new ArrayList<>(base.almacenes());
        var pendientes = new LinkedHashMap<String, Pedido>();
        var originales = new HashMap<String, Pedido>();
        var entregados = new HashMap<String, Integer>();
        var descansos = new HashMap<String, LocalDateTime>();
        for (String codigo : base.descansoRealizado())
            descansos.put(codigo, base.instante());
        var eventos = new ArrayList<Entrega>();
        var demanda = datos.pedidos().stream()
                .sorted(Comparator.comparing(Pedido::fechaRegistro).thenComparing(Pedido::id)).toList();
        for (var p : demanda) {
            double cantidad = Math.ceil(p.cantidad() * factor);
            if (cantidad > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Carga excesiva");
            var q = new Pedido(p.id(), p.fechaRegistro(), p.ubicacion(), (int) cantidad, p.plazoHoras(), p.clienteId());
            if (originales.put(q.id(), q) != null)
                throw new IllegalArgumentException("Pedido duplicado");
        }
        LocalDateTime t = base.instante();
        int indice = 0, ciclos = 0, completos = 0, paquetes = 0;
        double sumaHolgura = 0, minima = Double.POSITIVE_INFINITY;
        double taTotal = 0, taMax = 0, distancia = 0, minutos = 0;
        int ejecuciones = 0, cargaDespachada = 0, capacidadDespachada = 0;
        Set<String> utilizados = new HashSet<>();
        String fin = "LIMITE_DE_CICLOS";
        Files.createDirectories(archivo.toAbsolutePath().getParent());
        try (var csv = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            csv.write(
                    "semilla,factor_carga,ciclo,instante,estado,Ta_ms,holgura_plan_promedio_min,holgura_plan_minima_min,distancia_plan_km,tiempo_plan_rutas_min,vehiculos_plan,utilizacion_capacidad_plan,pedidos_plan,paquetes_sin_plan,rutas_despachadas,pedidos_entregados_acum,paquetes_entregados_acum,holgura_real_promedio_min,holgura_real_minima_min\n");
            while (true) {
                for (var it = eventos.iterator(); it.hasNext();) {
                    var evento = it.next();
                    if (evento.fin().isAfter(t))
                        continue;
                    var parte = evento.parte();
                    int total = entregados.merge(parte.pedido().id(), parte.cantidad(), Integer::sum);
                    paquetes += parte.cantidad();
                    var original = originales.get(parte.pedido().id());
                    if (total == original.cantidad()) {
                        completos++;
                        double holgura = Duration.between(evento.fin(), original.deadline()).toNanos() / 60e9;
                        sumaHolgura += holgura;
                        minima = Math.min(minima, holgura);
                    }
                    it.remove();
                }
                while (indice < demanda.size() && !demanda.get(indice).fechaRegistro().isAfter(t)) {
                    var p = originales.get(demanda.get(indice++).id());
                    pendientes.put(p.id(), p);
                }
                boolean retornaron = true;
                for (var v : flota)
                    if (v.disponibleDesde().isAfter(t))
                        retornaron = false;
                if (indice == demanda.size() && pendientes.isEmpty() && eventos.isEmpty() && retornaron) {
                    fin = "FIN_DE_DATOS";
                    break;
                }
                if (maxCiclos > 0 && ciclos >= maxCiclos)
                    break;
                Set<String> descanso = descansosCompletados(base, flota, t, par, descansos);
                var estado = new EstadoOperacion(t, new ArrayList<>(pendientes.values()), flota, almacenes,
                        base.bloqueos(), base.averias(), base.mantenimientos(), List.of(), descanso);
                ResultadoPlanificacion resultado = pendientes.isEmpty() ? null : motor.planificar(estado, par);
                if (resultado != null) {
                    ejecuciones++;
                    taTotal += resultado.metricas().taMs();
                    taMax = Math.max(taMax, resultado.metricas().taMs());
                }
                if (resultado != null && !resultado.evaluacion().factible())
                    throw new IllegalStateException("Salida invalida: " + resultado.evaluacion().errores());
                boolean colapso = resultado != null && !resultado.evaluacion().completa();
                int despachadas = 0;
                var siguiente = t.plusMinutes(sa);
                if (resultado != null && !colapso)
                    for (var ruta : resultado.evaluacion().rutas()) {
                        if (ruta.ruta().partes().isEmpty() || !ruta.salida().isBefore(siguiente))
                            continue;
                        despachadas++;
                        distancia += ruta.distanciaKm();
                        minutos += ruta.minutos();
                        utilizados.add(ruta.ruta().vehiculo());
                        cargaDespachada += ruta.ruta().carga();
                        capacidadDespachada += TipoVehiculo.desdeCodigo(ruta.ruta().vehiculo()).capacidad();
                        for (var parada : ruta.paradas())
                            for (var parte : parada.partes()) {
                                eventos.add(new Entrega(parte, parada.finServicio()));
                                var p = pendientes.get(parte.pedido().id());
                                int restante = p.cantidad() - parte.cantidad();
                                if (restante == 0)
                                    pendientes.remove(p.id());
                                else
                                    pendientes.put(p.id(), new Pedido(p.id(), p.fechaRegistro(), p.ubicacion(),
                                            restante, p.plazoHoras(), p.clienteId()));
                            }
                        for (int i = 0; i < almacenes.size(); i++) {
                            var a = almacenes.get(i);
                            if (a.id().equals(ruta.ruta().almacenOrigen()) && !a.ilimitado())
                                almacenes.set(i, new Almacen(a.id(), a.nodo(), a.stock() - ruta.ruta().carga(), false));
                        }
                        var destino = almacenes.stream().filter(a -> a.id().equals(ruta.almacenRetorno())).findFirst()
                                .orElseThrow();
                        for (int i = 0; i < flota.size(); i++) {
                            var v = flota.get(i);
                            if (v.codigo().equals(ruta.ruta().vehiculo()))
                                flota.set(i, new Vehiculo(v.codigo(), v.tipo(), destino.nodo(), true, ruta.fin()));
                        }
                        if (ruta.descansoFin() != null)
                            descansos.put(ruta.ruta().vehiculo(), ruta.descansoFin());
                    }
                // Orden cronologico obligatorio para completar un pedido con su ultima parte.
                eventos.sort(Comparator.comparing(Entrega::fin));
                var m = resultado == null ? null : resultado.metricas();
                csv.write(semilla + "," + factor + "," + ciclos + "," + t + ","
                        + (colapso ? "COLAPSO_PLANIFICACION" : m == null ? "SIN_DEMANDA" : "COMPLETA") + ","
                        + (m == null ? "0,,,,,,,0,0"
                                : m.taMs() + "," + celda(m.holguraPromedioMin()) + "," + celda(m.holguraMinimaMin())
                                        + "," + m.distanciaKm() + "," + m.tiempoRutasMinutos() + ","
                                        + m.vehiculosUsados() + "," + m.utilizacionCapacidad() + ","
                                        + m.pedidosTotales() + "," + m.paquetesPendientes())
                        + "," + despachadas + "," + completos + "," + paquetes + ","
                        + (completos == 0 ? "," : sumaHolgura / completos + "," + minima) + "\n");
                ciclos++;
                if (colapso) {
                    fin = "COLAPSO_PLANIFICACION";
                    break;
                }
                if (!siguiente.toLocalDate().equals(t.toLocalDate()))
                    almacenes = new ArrayList<>(base.almacenes());
                t = siguiente;
            }
        }
        return new Resumen(fin, t, ciclos, completos, paquetes, completos == 0 ? Double.NaN : sumaHolgura / completos,
                completos == 0 ? Double.NaN : minima, ejecuciones, taTotal, taMax, distancia, minutos,
                utilizados.size(), capacidadDespachada == 0 ? 0 : (double) cargaDespachada / capacidadDespachada);
    }

    private static String celda(Double valor) {
        return valor == null ? "" : valor.toString();
    }

    private static String minutos(double valor) {
        return Double.isNaN(valor) ? "N/D" : String.format(Locale.ROOT, "%.2f", valor);
    }

    /**
     * Carga meses consecutivos; exige bloqueos de cada mes para no omitir
     * incidencias silenciosamente.
     */
    private static Datos cargar(Path ventas, Path bloqueos, YearMonth mes) throws IOException {
        var inicio = mes.atDay(1).atStartOfDay();
        var pedidos = new ArrayList<Pedido>();
        var cierres = new ArrayList<Bloqueo>();
        EstadoOperacion base = null;
        for (var actual = mes;; actual = actual.plusMonths(1)) {
            Path v = ventas.resolve("ventas." + actual.toString().replace("-", "") + ".txt");
            if (!Files.exists(v))
                break;
            Path b = bloqueos.resolve(
                    String.format(Locale.ROOT, "bloqueo.%02d%02d.txt", actual.getYear() % 100, actual.getMonthValue()));
            if (base == null)
                base = new DatasetLoader().cargarExperimental(v, b, inicio, 48, Integer.MAX_VALUE).estado();
            pedidos.addAll(new PedidoParser().leer(v, actual.getYear(), actual.getMonthValue()));
            cierres.addAll(new BloqueoParser().leer(b, actual.getYear(), actual.getMonthValue()));
        }
        if (base == null)
            throw new IllegalArgumentException("No hay ventas del mes inicial");
        return new Datos(new EstadoOperacion(inicio, List.of(), base.vehiculos(), base.almacenes(), cierres,
                base.averias(), base.mantenimientos(), List.of(), Set.of()), pedidos);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6 || args.length > 11)
            throw new IllegalArgumentException(
                    "Uso: SimulacionComparada TS|ALNS|AMBOS carpetaVentas carpetaBloqueos - AAAA-MM salida [iteraciones=300] [semillas=1,2,3] [maxCiclos=0] [Sa=10] [factorCarga=1]\n"
                            + "maxCiclos=0 (por defecto) es el modo oficial de comparacion 'hasta el colapso o fin de datos', sin limite de dias.\n"
                            + "Para una ventana acotada use maxCiclos=720 con Sa=10 (5 dias completos).\n"
                            + "Solo bloqueos; averias y mantenimiento excluidos de la experimentacion.");
        String seleccion = args[0].toUpperCase(Locale.ROOT);
        if (!Set.of("TS", "ALNS", "AMBOS").contains(seleccion))
            throw new IllegalArgumentException("Algoritmo desconocido");
        int iter = args.length > 6 ? Integer.parseInt(args[6]) : 300;
        String semillas = args.length > 7 ? args[7] : "1,2,3";
        int ciclos = args.length > 8 ? Integer.parseInt(args[8]) : 0;
        int sa = args.length > 9 ? Integer.parseInt(args[9]) : 10;
        double factor = args.length > 10 ? Double.parseDouble(args[10]) : 1;
        if (iter <= 0 || ciclos < 0 || sa <= 0 || !Double.isFinite(factor) || factor <= 0)
            throw new IllegalArgumentException("Iteraciones, Sa y factor positivos; ciclos >= 0");
        Set<Long> semillasValidas = new HashSet<>();
        for (String s : semillas.split(",", -1))
            if (!semillasValidas.add(Long.parseLong(s.trim())))
                throw new IllegalArgumentException("Semilla repetida");
        if (!args[3].equals("-"))
            System.out.println(
                    "Experimentacion: mantenimiento excluido; se ignora el argumento legado. Use '-' en esa posicion.");
        var datos = cargar(Path.of(args[1]), Path.of(args[2]), YearMonth.parse(args[4]));
        Path salida = Path.of(args[5]);
        if (Files.exists(salida))
            throw new IllegalArgumentException("Use una carpeta nueva para preservar corridas anteriores");
        Files.createDirectories(salida);
        Files.writeString(salida.resolve("metadatos.txt"),
                "Reglas experimentales v3: bloqueos incluidos, sin averias ni mantenimiento; alimentacion v2\nArgumentos: "
                        + Arrays.toString(args) + "\nJava: " + System.getProperty("java.version") + "\nParametros: "
                        + ParametrosOperacion.porDefecto() + "\nAlmacenes: " + datos.base().almacenes() + "\nPedidos: "
                        + datos.pedidos().size()
                        + "\nSolo pedidos registrados; sin anticipacion de demanda. Inicio sin operaciones previas.\n");
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update(datos.base().toString().getBytes(StandardCharsets.UTF_8));
        for (var pedido : datos.pedidos())
            digest.update(pedido.toString().getBytes(StandardCharsets.UTF_8));
        Files.writeString(salida.resolve("metadatos.txt"), "Instancia SHA-256: "
                + HexFormat.of().formatHex(digest.digest()) + "\nIteraciones=" + iter + "; semillas=" + semillas
                + "; Sa=" + sa + "; ciclos=" + ciclos + "; factor=" + factor
                + "\nTS: tenencia=7, candidatos=400; ALNS: destruccion=4, segmento=5, reaccion=0.7, temperatura=0.05; presupuesto temporal=0"
                + "\nAverias: excluidas. Mantenimiento: excluido. Bloqueos: archivos mensuales suministrados.\n",
                StandardOpenOption.APPEND);
        try (var resumen = Files.newBufferedWriter(salida.resolve("resumen.csv"), StandardCharsets.UTF_8)) {
            resumen.write(
                    "algoritmo,semilla,factor_carga,fin,instante_final,duracion_dias,ciclos,pedidos_completos,paquetes_entregados,holgura_real_promedio_min,holgura_real_minima_min,ejecuciones,Ta_total_ms,Ta_promedio_ms,Ta_max_ms,distancia_despachada_km,tiempo_rutas_despachadas_min,vehiculos_utilizados,utilizacion_capacidad\n");
            Set<Long> usadas = new HashSet<>();
            for (String s : semillas.split(",")) {
                long semilla = Long.parseLong(s.trim());
                if (!usadas.add(semilla))
                    throw new IllegalArgumentException("Semilla repetida");
                for (String algoritmo : seleccion.equals("AMBOS") ? List.of("TS", "ALNS") : List.of(seleccion)) {
                    PlanificadorEstricto motor = algoritmo.equals("TS")
                            ? new TabuSearchPlanner(new ConfiguracionTabu(iter, 7, Math.max(1, iter), 400, 0, semilla))
                            : new ALNSPlanner(
                                    new ConfiguracionALNS(iter, Math.max(1, iter), 4, 5, .7, .05, 0, semilla));
                    System.out.println("Ejecutando " + algoritmo + " semilla=" + semilla);
                    var r = ejecutar(datos, motor, sa, ciclos, salida.resolve(algoritmo + "-" + semilla + ".csv"),
                            semilla, factor);
                    double dias = Duration.between(datos.base().instante(), r.instante()).toMinutes() / 1440.0;
                    resumen.write(algoritmo + "," + semilla + "," + factor + "," + r.fin() + "," + r.instante() + ","
                            + dias + "," + r.ciclos() + "," + r.completos() + "," + r.paquetesEntregados() + ","
                            + (Double.isNaN(r.holguraMedia()) ? "," : r.holguraMedia() + "," + r.holguraMinima()) + ","
                            + r.ejecuciones() + "," + r.taTotalMs() + ","
                            + (r.ejecuciones() == 0 ? "" : r.taTotalMs() / r.ejecuciones()) + "," + r.taMaxMs() + ","
                            + r.distanciaKm() + "," + r.minutosRutas() + "," + r.vehiculos() + "," + r.utilizacion()
                            + "\n");
                    resumen.flush();
                    System.out.printf(Locale.ROOT,
                            "%s | %s | %.3f dias | %d pedidos entregados | %d paquetes | holgura real media/min: %s / %s min%n",
                            algoritmo, r.fin(), dias, r.completos(), r.paquetesEntregados(), minutos(r.holguraMedia()),
                            minutos(r.holguraMinima()));
                    System.out.printf(Locale.ROOT,
                            "Ta total/media/max: %.2f / %.2f / %.2f ms | %.2f km | %.2f min rutas | %d vehiculos | capacidad %.1f%%%n",
                            r.taTotalMs(), r.ejecuciones() == 0 ? 0 : r.taTotalMs() / r.ejecuciones(), r.taMaxMs(),
                            r.distanciaKm(), r.minutosRutas(), r.vehiculos(), 100 * r.utilizacion());
                }
            }
        }
        System.out.println("Resultados: " + salida.toAbsolutePath());
    }
}
