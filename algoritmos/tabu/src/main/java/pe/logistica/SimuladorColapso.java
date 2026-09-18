package pe.logistica;

import pe.logistica.data.DatasetLoader;
import pe.logistica.model.*;
import pe.logistica.tabu.TabuSearchPlanner;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Arnes de simulacion continua para Busqueda Tabu: encadena ciclos de planificacion (cada uno un
 * EstadoOperacion nuevo, Sa minutos despues del anterior) hasta que un ciclo no produzca un plan
 * factible (COLAPSO LOGISTICO) o se agote el numero maximo de ciclos.
 *
 * Es el equivalente de DemoPlanificador (algoritmos/alns) para TS, pensado para que ambos
 * algoritmos puedan compararse con el mismo fitness: tiempo/ciclos sostenidos antes del primer
 * incumplimiento.
 *
 * Diferencia importante frente a ALNS: aqui un pedido dentro de la ventana de un ciclo debe
 * quedar 100% cubierto en ese mismo ciclo para que el ciclo sea factible (no existe el concepto
 * de "demanda pendiente" que se arrastra varios ciclos, como en pedidosVivos de DemoPlanificador).
 * Por eso el colapso de TS puede ser mas temprano que el de ALNS aunque ambos usen las mismas
 * restricciones duras: TS no tiene margen para posponer un pedido dificil a un ciclo posterior.
 *
 * Uso:
 *   java pe.logistica.SimuladorColapso --datos datos [--dia N] [--hora N] [--anio AAAA] [--mes MM]
 *        [--sa MIN] [--k N] [--iteraciones N] [--tenencia N] [--ciclos N] [--central x,y]
 */
public final class SimuladorColapso {
    private SimuladorColapso() { }

    public static void main(String[] args) throws IOException {
        System.exit(ejecutar(args, System.out, System.err));
    }

    public static int ejecutar(String[] args, PrintStream out, PrintStream err) {
        try {
            Map<String, String> opciones = opciones(args);
            if (!opciones.containsKey("datos")) {
                out.println("Uso: java pe.logistica.SimuladorColapso --datos <carpeta> [--dia N] [--hora N]"
                        + " [--anio AAAA] [--mes MM] [--sa MIN] [--k N] [--iteraciones N] [--tenencia N]"
                        + " [--ciclos N] [--central x,y]");
                return 2;
            }
            int anio = Integer.parseInt(opciones.getOrDefault("anio", "2026"));
            int mes = Integer.parseInt(opciones.getOrDefault("mes", "9"));
            int dia = Integer.parseInt(opciones.getOrDefault("dia", "1"));
            int hora = Integer.parseInt(opciones.getOrDefault("hora", "0"));
            LocalDateTime tInicio = LocalDateTime.of(anio, mes, dia, hora, 0);

            var config = new ConfiguracionTabu(
                    Long.parseLong(opciones.getOrDefault("sa", "30")),
                    Integer.parseInt(opciones.getOrDefault("k", "4")),
                    Integer.parseInt(opciones.getOrDefault("iteraciones", "100")),
                    Integer.parseInt(opciones.getOrDefault("tenencia", "7")));
            int maxCiclos = Integer.parseInt(opciones.getOrDefault("ciclos", "200"));

            Nodo central = nodo(opciones.getOrDefault("central", "27,14"));
            List<Vehiculo> flotaBase = ReferenciaProyecto.flota(central);
            ParametrosOperacion parametros = ParametrosOperacion.publicados();
            Path raiz = Path.of(opciones.get("datos"));
            var loader = new DatasetLoader();

            out.println("===================================================================");
            out.println(" PaqRap - Componente planificador - Busqueda Tabu (simulacion hasta colapso)");
            out.println("===================================================================");
            out.printf("Inicio: %s | Sa=%d min K=%d Sc=%d min | maxIteraciones=%d tenencia=%d | ciclos<=%d%n%n",
                    tInicio, config.saMinutos(), config.k(), config.scMinutos(),
                    config.maxIteraciones(), config.tenenciaTabu(), maxCiclos);

            Map<String, LocalDateTime> disponibleDesde = new HashMap<>();
            Set<String> entregados = new HashSet<>();
            LocalDateTime t = tInicio;
            int ciclosSostenidos = 0;
            int entregadosTotal = 0;
            double kmTotal = 0, costoTotal = 0;
            boolean colapso = false;
            String motivoColapso = null;

            int c;
            for (c = 0; c < maxCiclos; c++) {
                var datos = loader.cargar(raiz, t, config.scMinutos());
                var pedidosPendientes = datos.pedidos().stream()
                        .filter(p -> !entregados.contains(p.id())).toList();

                List<Vehiculo> flota = flotaBase.stream()
                        .map(v -> new Vehiculo(v.codigo(), v.tipo(), v.ubicacionInicial(), v.disponible(),
                                disponibleDesde.getOrDefault(v.codigo(), v.disponibleDesde())))
                        .toList();

                var estado = new EstadoOperacion(t, pedidosPendientes, flota, datos.bloqueos(),
                        datos.mantenimientos(), parametros);
                var resultado = new TabuSearchPlanner().ejecutar(estado, config);
                var m = resultado.metricas();

                out.printf("--- Ciclo %-4d %s | pedidos en ventana: %-3d | %s%n", c + 1, t,
                        pedidosPendientes.size(), m.factibilidadGlobal() ? "FACTIBLE" : "NO FACTIBLE");

                if (!m.factibilidadGlobal()) {
                    colapso = true;
                    motivoColapso = String.format(Locale.ROOT,
                            "%s: %d/%d pedidos sin cubrir en la ventana (motivo interno: %s)",
                            t, m.pedidosNoAsignados(), m.pedidosConsiderados(), m.rendimiento().motivoParada());
                    out.println("    COLAPSO LOGISTICO: " + motivoColapso);
                    break;
                }

                ciclosSostenidos++;
                entregadosTotal += m.pedidosAsignados();
                kmTotal += m.distanciaTotalKm();
                costoTotal += m.costoTotal();
                for (ResultadoRuta rr : resultado.evaluacion().rutas()) {
                    if (rr.ruta().entregas().isEmpty()) continue;
                    disponibleDesde.put(rr.ruta().vehiculo().codigo(), rr.fin());
                    for (Entrega e : rr.ruta().entregas()) entregados.add(e.pedido().id());
                }
                if (m.pedidosConsiderados() > 0) {
                    out.printf("    asignados=%d | km=%.0f | S/ %.2f | Ta=%.1f ms%n",
                            m.pedidosAsignados(), m.distanciaTotalKm(), m.costoTotal(), m.taMs());
                }
                t = t.plusMinutes(config.saMinutos());
            }

            Duration sostenido = Duration.between(tInicio, t);
            out.println("===================================================================");
            out.printf(Locale.ROOT, "FITNESS (tiempo sostenido antes de colapso): %d ciclos = %d min = %.2f h%n",
                    ciclosSostenidos, sostenido.toMinutes(), sostenido.toMinutes() / 60.0);
            out.printf(Locale.ROOT, "Entregados: %d | Distancia: %.0f km | Costo: S/ %.2f | %s%n",
                    entregadosTotal, kmTotal, costoTotal, colapso ? "COLAPSO" : "SIN COLAPSO (fin de ciclos pedidos)");
            if (colapso) out.println("Motivo: " + motivoColapso);
            out.println("===================================================================");
            return 0;
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException e) {
            err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static Nodo nodo(String texto) {
        String[] xy = texto.split(",", -1);
        if (xy.length != 2) throw new IllegalArgumentException("Coordenada esperada: x,y");
        return new Nodo(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
    }

    private static Map<String, String> opciones(String[] args) {
        Map<String, String> resultado = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Argumento inesperado: " + args[i]);
            String clave = args[i].substring(2);
            if (++i >= args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Falta valor para " + clave);
            resultado.put(clave, args[i]);
        }
        return resultado;
    }
}
