package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.alns.reparacion.InsercionPorArrepentimiento;
import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.planificador.Planificador;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.io.PrintStream;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * Diagnóstico del ciclo en que la simulación colapsa: por qué el planificador no encontró un
 * plan factible.
 *
 * <p>Contrasta las causas posibles con experimentos sobre el mismo instante:</p>
 * <ul>
 *   <li><b>Demanda</b>: pedidos y paquetes de la ventana frente a la capacidad de la flota en un
 *       solo viaje, viajes del plan y pedidos reprogramados.</li>
 *   <li><b>Pedido aislado</b>: si el pedido no asignado cabe, solo, en alguna unidad; si cabe
 *       en un segundo viaje de una unidad, después de la ruta que ya tiene en el plan.</li>
 *   <li><b>Bloqueos</b>: las mismas pruebas y una replanificación sobre un mapa sin bloqueos.</li>
 *   <li><b>Heurística</b>: replanificación desde cero (sin plan previo) e inserción por
 *       arrepentimiento desde una solución vacía.</li>
 *   <li><b>Mantenimiento</b>: unidades fuera de servicio ese día.</li>
 * </ul>
 * <p>No modifica el estado de la simulación.</p>
 */
final class DiagnosticoColapso {

    private final Instancia instancia;
    private final ContextoPlanificacion ctx;
    private final Solucion plan;
    private final Solucion planVigente;
    private final Planificador planificador;
    private final ParametrosSimulacion par;
    private final ParametrosPlanificador parPlan;
    private final List<Pedido> pedidosVivos;
    private final PrintStream salida;
    private final IntFunction<String> formato;

    DiagnosticoColapso(Instancia instancia, ContextoPlanificacion ctx, Solucion plan, Solucion planVigente,
                       Planificador planificador, ParametrosSimulacion par, ParametrosPlanificador parPlan,
                       List<Pedido> pedidosVivos, PrintStream salida, IntFunction<String> formato) {
        this.instancia = instancia;
        this.ctx = ctx;
        this.plan = plan;
        this.planVigente = planVigente;
        this.planificador = planificador;
        this.par = par;
        this.parPlan = parPlan;
        this.pedidosVivos = pedidosVivos;
        this.salida = salida;
        this.formato = formato;
    }

    /** Resultado de probar un pedido en una unidad. */
    private static final class Prueba {
        int factibles;
        final Map<String, Integer> motivos = new LinkedHashMap<>();
        int mejorLlegada = Integer.MAX_VALUE;

        void registrar(Ruta r) {
            if (r.esFactible()) {
                factibles++;
                mejorLlegada = Math.min(mejorLlegada, r.minutoLlegada(0));
            } else {
                String m = r.getMotivoInfactibilidad().replaceAll("P\\d+ \\(.*", "(tarde)")
                        .replaceAll("\\(\\d+>\\d+\\)", "").replaceAll("el día \\d+", "").trim();
                motivos.merge(m, 1, Integer::sum);
            }
        }
    }

    /**
     * Imprime el diagnóstico y devuelve un resumen de una línea con los resultados clave, para
     * el CSV de la corrida.
     */
    String ejecutar() {
        int t = ctx.getMinutoActual();
        salida.println("    ---------------------------------------------------------------- diagnóstico");

        // 1. Demanda frente a la capacidad de un viaje
        int paquetes = 0;
        int atrasados = 0;
        Map<Integer, int[]> porPlazo = new LinkedHashMap<>();
        for (Pedido p : ctx.getPedidosPorAtender()) {
            paquetes += p.getCantidad();
            if (p.getMinutoRegistro() <= t) {
                atrasados++;
            }
            int[] c = porPlazo.computeIfAbsent(p.getPlazoHoras(), k -> new int[2]);
            c[0]++;
            c[1] += p.getCantidad();
        }
        int capacidadViaje = 0;
        int libres = 0;
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            capacidadViaje += v.getCapacidad();
            if (v.getMinutoDisponibleDesde() <= t) {
                libres++;
            }
        }
        int cargaPlan = 0;
        int rutasPlan = 0;
        int viajesPlan = 0;
        int viajesLlenos = 0;
        for (Ruta r : plan.getRutas()) {
            if (!r.estaVacia()) {
                rutasPlan++;
                cargaPlan += r.getCargaTotal();
                for (Ruta.Viaje v : r.getViajes()) {
                    viajesPlan++;
                    if (v.getCarga() == r.getVehiculo().getCapacidad()) {
                        viajesLlenos++;
                    }
                }
            }
        }
        salida.printf("    [demanda] %d pedidos / %d paquetes en la ventana (%d ya registrados, %d por"
                        + " registrarse hasta %s); por plazo (h: pedidos/paquetes) %s%n",
                ctx.getPedidosPorAtender().size(), paquetes, atrasados,
                ctx.getPedidosPorAtender().size() - atrasados, formato.apply(t + par.scMinutos),
                formatoPlazos(porPlazo));
        salida.printf("    [demanda] capacidad de la flota en un viaje: %d paquetes (%.0f%% ocupada por la"
                        + " ventana); el plan usa %d rutas y %d viajes con %d paquetes, %d viajes llenos;"
                        + " reprograma %d pedidos / %d paquetes%n",
                capacidadViaje, 100.0 * paquetes / Math.max(1, capacidadViaje), rutasPlan, viajesPlan,
                cargaPlan, viajesLlenos, plan.getPedidosPostergados(), plan.getPaquetesPostergados());

        // 2. Flota y mantenimiento
        List<String> mant = instancia.unidadesEnMantenimiento(Turnos.dia(t));
        salida.printf("    [flota] %d unidades asignables (%d libres en T, %d en ruta); %d en mantenimiento"
                        + " ese día%s%n",
                ctx.getUnidadesAsignables().size(), libres, ctx.getUnidadesAsignables().size() - libres,
                mant.size(), mant.isEmpty() ? "" : " " + mant);

        // 3. El pedido no asignado
        ContextoPlanificacion ctxSinBloqueos = contextoSinBloqueos(t);
        int soloFactible = 0;
        int segundoViaje = 0;
        int soloSinBloqueos = 0;
        List<Pedido> noAsignados = new ArrayList<>(plan.getNoAsignados());
        noAsignados.removeIf(ctx::esPostergable);   // los reprogramables no causan el colapso
        for (Pedido p : noAsignados.subList(0, Math.min(3, noAsignados.size()))) {
            int dist = Integer.MAX_VALUE;
            for (Almacen a : ctx.getAlmacenes()) {
                dist = Math.min(dist, a.getUbicacion().distanciaManhattan(p.getDestino()));
            }
            salida.printf("    [pedido] %s: registro %s, plazo %d h, límite %s (holgura desde T: %d min),"
                            + " %d km del almacén más cercano%n",
                    p, formato.apply(p.getMinutoRegistro()), p.getPlazoHoras(),
                    formato.apply(p.getMinutoLimite()), p.getMinutoLimite() - t, dist);

            Prueba solo = probarSolo(p, ctx);
            Prueba segundo = probarSegundoViaje(p);
            Prueba sinBloq = probarSolo(p, ctxSinBloqueos);
            salida.printf("    [pedido]   solo en una ruta vacía: factible en %d de %d unidades%s; motivos: %s%n",
                    solo.factibles, ctx.getUnidadesAsignables().size(),
                    solo.factibles > 0 ? " (mejor llegada " + formato.apply(solo.mejorLlegada) + ")" : "",
                    solo.motivos);
            salida.printf("    [pedido]   en un segundo viaje tras la ruta del plan: factible en %d unidades%s%n",
                    segundo.factibles,
                    segundo.factibles > 0 ? " (mejor llegada " + formato.apply(segundo.mejorLlegada) + ")" : "");
            salida.printf("    [pedido]   solo, sin bloqueos: factible en %d unidades%n", sinBloq.factibles);
            if (p == noAsignados.get(0)) {
                soloFactible = solo.factibles;
                segundoViaje = segundo.factibles;
                soloSinBloqueos = sinBloq.factibles;
            }
        }

        // 4. Replanificaciones alternativas
        boolean sinBloqueos = planificador.planificar(ctxSinBloqueos, planVigente).esFactible();
        boolean desdeCero = planificador.planificar(ctx, null).esFactible();
        Solucion arrep = new Solucion();
        new InsercionPorArrepentimiento(1_000_000.0).reparar(arrep, ctx.getPedidosPorAtender(), ctx, new Random(1));
        arrep.evaluar(ctx);
        boolean arrepentimiento = arrep.esFactible();
        salida.printf("    [replan] sin bloqueos: %s · desde cero (sin plan previo): %s · arrepentimiento"
                        + " desde vacío: %s (%d sin asignar)%n",
                factible(sinBloqueos), factible(desdeCero), factible(arrepentimiento),
                arrep.getNoAsignados().size());

        // 5. Conclusión
        String causa;
        if (!mant.isEmpty() && soloFactible == 0) {
            causa = "mantenimiento";
        } else if (sinBloqueos && !desdeCero && !arrepentimiento) {
            causa = "bloqueos";
        } else if (desdeCero || arrepentimiento) {
            causa = "heuristica";
        } else if (soloFactible > 0 || segundoViaje > 0) {
            causa = "un_viaje_por_unidad";
        } else if (soloSinBloqueos > 0) {
            causa = "bloqueos";
        } else {
            causa = "plazo_inalcanzable";
        }
        String resumen = String.format("causa=%s; ventana=%d pedidos/%d paquetes; capacidad_viaje=%d;"
                        + " solo_factible=%d; segundo_viaje=%d; sin_bloqueos=%s; desde_cero=%s;"
                        + " arrepentimiento=%s; mantenimiento=%d",
                causa, ctx.getPedidosPorAtender().size(), paquetes, capacidadViaje, soloFactible,
                segundoViaje, factible(sinBloqueos), factible(desdeCero), factible(arrepentimiento), mant.size());
        salida.println("    [conclusión] " + resumen);
        return resumen;
    }

    private static String factible(boolean f) {
        return f ? "factible" : "no_factible";
    }

    private static String formatoPlazos(Map<Integer, int[]> porPlazo) {
        StringBuilder sb = new StringBuilder();
        for (Integer h : new java.util.TreeSet<>(porPlazo.keySet())) {
            int[] c = porPlazo.get(h);
            sb.append(sb.length() == 0 ? "" : ", ").append(h).append(':').append(c[0]).append('/').append(c[1]);
        }
        return sb.toString();
    }

    /** El pedido, solo, en una ruta vacía de cada unidad y desde cada almacén. */
    private Prueba probarSolo(Pedido p, ContextoPlanificacion c) {
        Prueba prueba = new Prueba();
        for (Vehiculo v : c.getUnidadesAsignables()) {
            Ruta mejor = null;
            for (Almacen a : c.getAlmacenes()) {
                Ruta r = new Ruta(v, a);
                r.insertar(0, p);
                r.recalcular(c);
                if (mejor == null || (r.esFactible() && !mejor.esFactible())) {
                    mejor = r;
                }
            }
            prueba.registrar(mejor);
        }
        return prueba;
    }

    /**
     * El pedido en un segundo viaje de cada unidad que ya tiene ruta en el plan: sale del
     * almacén de retorno cuando termina esa ruta. El plan no admite dos rutas por unidad; esta
     * prueba mide si esa restricción es la que impide atender el pedido.
     */
    private Prueba probarSegundoViaje(Pedido p) {
        Prueba prueba = new Prueba();
        for (Ruta primera : plan.getRutas()) {
            if (primera.estaVacia()) {
                continue;
            }
            Vehiculo original = primera.getVehiculo();
            Vehiculo v = new Vehiculo(original.getCodigo(), original.getTipo(),
                    primera.getAlmacenRetorno().getUbicacion());
            v.setMinutoDisponibleDesde(primera.getMinutoRetorno());
            v.setEstado(Vehiculo.Estado.EN_RUTA);
            v.setTurnoDeUltimaAlimentacion(Math.max(original.getTurnoDeUltimaAlimentacion(),
                    primera.turnoDeAlimentacionEnViajes(primera.getViajes().size())));
            Ruta mejor = null;
            for (Almacen a : ctx.getAlmacenes()) {
                Ruta r = new Ruta(v, a);
                r.insertar(0, p);
                r.recalcular(ctx);
                if (mejor == null || (r.esFactible() && !mejor.esFactible())) {
                    mejor = r;
                }
            }
            prueba.registrar(mejor);
        }
        return prueba;
    }

    /** El mismo instante sobre un mapa sin bloqueos. */
    private ContextoPlanificacion contextoSinBloqueos(int t) {
        YearMonth mes = YearMonth.from(instancia.getFechaInicio());
        Instancia sinBloqueos = new Instancia(new MapaUrbano(Collections.emptyList()), instancia.getAlmacenes(),
                instancia.getFlota(), instancia.getPedidos(), instancia.getMantenimientos(),
                mes.getYear(), mes.getMonthValue());
        return ContextoPlanificacion.construir(sinBloqueos, t, par.scMinutos, pedidosVivos,
                Collections.emptyList(), parPlan);
    }
}
