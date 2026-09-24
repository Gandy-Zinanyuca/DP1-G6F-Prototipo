package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.datos.CargadorBloqueos;
import pe.pucp.paqrap.datos.CargadorVentas;
import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Bloqueo;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.planificador.Planificador;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Simulación con reloj discreto sobre el componente planificador.
 *
 * <h2>Avance temporal</h2>
 * <p>Cada ciclo ocurre en un instante T; el siguiente, en T + Sa. En cada ciclo:</p>
 * <ol>
 *   <li>Se registran las entregas cuya llegada ya ocurrió (≤ T) y se liberan las unidades que
 *       regresaron a un almacén. Un pedido se marca como entregado solo cuando llegó su última
 *       parte.</li>
 *   <li>A medianoche se recargan los almacenes intermedios (LE033).</li>
 *   <li>Se planifica la cantidad no despachada de los pedidos de la ventana (pendientes más los
 *       registrados en (T, T + Sc]), partiendo del plan vigente.</li>
 *   <li>Se <b>despachan</b> solo las rutas que salen antes del siguiente ciclo (inicio &lt;
 *       T + Sa): la unidad carga en el almacén y queda comprometida hasta su regreso. Las
 *       rutas que salen después siguen siendo parte del plan y se replanifican en el ciclo
 *       siguiente, con la información nueva.</li>
 * </ol>
 *
 * <h2>Meses encadenados</h2>
 * <p>El reloj cuenta minutos desde el día 1, 00:00, del mes inicial. Cuando la ventana de
 * planificación se acerca al fin del último mes cargado, se cargan las ventas y los bloqueos
 * del mes siguiente con el desplazamiento correspondiente, sin interrumpir la simulación.</p>
 *
 * <h2>Colapso</h2>
 * <p>La simulación colapsa cuando un pedido no puede entregarse dentro de su plazo: el
 * planificador no encuentra un plan factible para los pedidos de la ventana (criterio del ISA),
 * una parte despachada llega tarde o vence el plazo de un pedido sin despachar. Sin límite de
 * ciclos, la simulación avanza hasta el colapso o hasta que se acaban los datos.</p>
 */
public class Simulador {

    /** Horizonte de carga anticipada: la ventana más los plazos más largos (36 h) y el regreso. */
    private static final int HORIZONTE_CARGA_MIN = 48 * 60;

    /** Parada de una ruta despachada, pendiente de entregar. */
    private static final class Parada {
        final Pedido parte;
        final int llegada;

        Parada(Pedido parte, int llegada) {
            this.parte = parte;
            this.llegada = llegada;
        }
    }

    private final Instancia instancia;
    private final FuentesDeDatos fuentes;
    private final Planificador planificador;
    private final ParametrosSimulacion par;
    private final ParametrosPlanificador parPlan;
    private final PrintStream salida;

    private final List<Pedido> pedidosVivos = new ArrayList<>();
    private final List<Parada> paradasEnCurso = new ArrayList<>();
    private final ResultadoSimulacion resultado = new ResultadoSimulacion();

    private final YearMonth mesInicial;
    private YearMonth ultimoMesCargado;
    private int inicioMesSiguiente;
    private boolean hayMasMeses = true;
    private int siguienteId;
    private int pedidosCargados;

    /**
     * @param instancia    instancia construida con el mes inicial (sus pedidos y bloqueos son
     *                     los de ese mes)
     * @param mesInicial   mes al que corresponde el minuto 0
     * @param fuentes      carpetas de donde se toman los meses siguientes
     */
    public Simulador(Instancia instancia, YearMonth mesInicial, FuentesDeDatos fuentes,
                     Planificador planificador, ParametrosSimulacion par,
                     ParametrosPlanificador parPlan, PrintStream salida) {
        this.instancia = instancia;
        this.fuentes = fuentes;
        this.planificador = planificador;
        this.par = par;
        this.parPlan = parPlan;
        this.salida = salida;
        this.mesInicial = mesInicial;
        this.ultimoMesCargado = mesInicial;
        this.inicioMesSiguiente = mesInicial.lengthOfMonth() * Turnos.MINUTOS_POR_DIA;
        this.siguienteId = 1;
        agregarPedidos(instancia.getPedidos());
        resultado.mesesCargados = 1;
        for (Vehiculo v : instancia.getFlota()) {
            resultado.unidadesPorTipo.merge(v.getTipo().name(), 1, Integer::sum);
        }
    }

    public ResultadoSimulacion getResultado() {
        return resultado;
    }

    /** Ejecuta la simulación hasta el colapso, el fin de los datos o el límite de ciclos. */
    public ResultadoSimulacion ejecutar() throws IOException {
        long inicioReal = System.nanoTime();
        int t = Turnos.aMinutos(par.diaInicial, par.horaInicial, 0);
        resultado.minutoInicial = t;
        resultado.instanteInicial = instante(t);

        prepararPendientesIniciales(t);

        BufferedWriter csv = abrirCsvCiclos();
        Solucion planVigente = null;
        int diaAnterior = Turnos.dia(t);
        long nsDia = 0;
        int ejecucionesDia = 0;

        try {
            while (true) {
                if (par.maxCiclos > 0 && resultado.ciclos >= par.maxCiclos) {
                    resultado.fin = ResultadoSimulacion.Fin.LIMITE_DE_CICLOS;
                    break;
                }
                cargarMesesHasta(t + par.scMinutos + HORIZONTE_CARGA_MIN, t);

                String tardia = registrarEntregas(t);
                if (tardia != null) {
                    colapsar(tardia);
                    break;
                }
                if (!hayMasMeses && pedidosVivos.isEmpty() && paradasEnCurso.isEmpty()) {
                    resultado.fin = ResultadoSimulacion.Fin.FIN_DE_DATOS;
                    break;
                }

                int dia = Turnos.dia(t);
                if (dia != diaAnterior) {
                    // Recarga de los intermedios a las 23:59:59 (LE033).
                    for (Almacen a : instancia.getAlmacenes()) {
                        a.recargar();
                    }
                    imprimirDia(diaAnterior, nsDia, ejecucionesDia);
                    diaAnterior = dia;
                    nsDia = 0;
                    ejecucionesDia = 0;
                }

                String vencido = plazoVencido(t);
                if (vencido != null) {
                    colapsar(vencido);
                    break;
                }

                ContextoPlanificacion ctx = ContextoPlanificacion.construir(instancia, t, par.scMinutos,
                        pedidosVivos, Collections.emptyList(), parPlan);

                if (ctx.getPedidosPorAtender().isEmpty()) {
                    escribirCiclo(csv, t, ctx, 0, true, 0, 0);
                    t += par.saMinutos;
                    resultado.ciclos++;
                    continue;
                }

                long ini = System.nanoTime();
                Solucion plan = planificador.planificar(ctx, planVigente);
                long ta = System.nanoTime() - ini;
                resultado.ejecucionesPlanificador++;
                resultado.tiempoPlanificadorNs += ta;
                resultado.taMaximoNs = Math.max(resultado.taMaximoNs, ta);
                nsDia += ta;
                ejecucionesDia++;

                if (!plan.esFactible()) {
                    escribirCiclo(csv, t, ctx, ta, false, 0, plan.getCostoOperacionSoles());
                    salida.printf("--- Ciclo %d · %s · pedidos por atender: %d · unidades: %d%n",
                            resultado.ciclos + 1, formatear(t), ctx.getPedidosPorAtender().size(),
                            ctx.getUnidadesAsignables().size());
                    salida.print(planificador.resumenUltimaEjecucion());
                    colapsar("no existe plan factible: " + String.join("; ", primeros(plan.getErrores(), 3)));
                    resultado.diagnosticoColapso = new DiagnosticoColapso(instancia, ctx, plan, planVigente,
                            planificador, par, parPlan, pedidosVivos, salida, this::formatear).ejecutar();
                    break;
                }

                resultado.maxPaquetesPostergados = Math.max(resultado.maxPaquetesPostergados,
                        plan.getPaquetesPostergados());
                if (plan.getPaquetesPostergados() > 0) {
                    resultado.ciclosConPostergacion++;
                }
                int despachadas = despachar(plan, t + par.saMinutos);
                escribirCiclo(csv, t, ctx, ta, true, despachadas, plan.getCostoOperacionSoles());
                if (par.detalle) {
                    salida.printf("--- Ciclo %d · %s · pedidos por atender: %d · unidades: %d · "
                                    + "bloqueos vigentes: %d · rutas despachadas: %d%n",
                            resultado.ciclos + 1, formatear(t), ctx.getPedidosPorAtender().size(),
                            ctx.getUnidadesAsignables().size(),
                            ctx.getMapa().getBloqueosVigentes().size(), despachadas);
                    salida.print(planificador.resumenUltimaEjecucion());
                }

                planVigente = plan.copia();
                t += par.saMinutos;
                resultado.ciclos++;
            }
        } finally {
            if (csv != null) {
                csv.close();
            }
        }

        resultado.minutoFinal = t;
        resultado.instanteFinal = instante(t);
        int futuros = 0;
        for (Pedido p : pedidosVivos) {
            if (p.getMinutoRegistro() > t) {
                futuros++;
            }
        }
        resultado.pedidosRegistrados = pedidosCargados - futuros;
        resultado.tiempoRealMs = (System.nanoTime() - inicioReal) / 1_000_000;
        if (par.csvResumen != null) {
            resultado.agregarACsv(par.csvResumen);
        }
        return resultado;
    }

    // ------------------------------------------------------------------ datos

    /**
     * Pedidos pendientes en el punto de inicio. Un pedido registrado antes del arranque sigue
     * pendiente si su plazo aún no venció: se mantiene y se planifica desde el primer ciclo,
     * aunque se haya registrado en el mes anterior (por eso se lee también ese archivo). Los
     * pedidos cuyo plazo venció antes del arranque pertenecen a la operación previa y quedan
     * fuera del periodo simulado. Los bloqueos del mes anterior que siguen vigentes también se
     * incorporan.
     */
    private void prepararPendientesIniciales(int t) throws IOException {
        int delMesAnterior = 0;
        YearMonth anterior = mesInicial.minusMonths(1);
        Path ventasAnterior = fuentes == null ? null : fuentes.ventas(anterior);
        if (ventasAnterior != null) {
            int desplazamiento = -anterior.lengthOfMonth() * Turnos.MINUTOS_POR_DIA;
            List<Pedido> arrastrados = new ArrayList<>();
            for (Pedido p : CargadorVentas.cargar(ventasAnterior, desplazamiento, siguienteId).pedidos) {
                if (p.getMinutoLimite() > t) {
                    arrastrados.add(p);
                }
            }
            agregarPedidos(arrastrados);
            delMesAnterior = arrastrados.size();
            Path bloqueosAnterior = fuentes.bloqueos(anterior);
            if (bloqueosAnterior != null) {
                List<Bloqueo> vigentes = new ArrayList<>();
                for (Bloqueo b : CargadorBloqueos.cargar(bloqueosAnterior, desplazamiento).bloqueos) {
                    if (b.getMinutoFin() >= t) {
                        vigentes.add(b);
                    }
                }
                instancia.getMapa().agregarBloqueos(vigentes);
            }
        }

        int antes = pedidosVivos.size();
        pedidosVivos.removeIf(p -> p.getMinutoLimite() <= t);
        int vencidos = antes - pedidosVivos.size();
        int pendientes = 0;
        for (Pedido p : pedidosVivos) {
            if (p.getMinutoRegistro() < t) {
                pendientes++;
            }
        }
        pedidosCargados = pedidosVivos.size();
        resultado.pedidosPendientesAlInicio = pendientes;
        salida.printf("Pedidos pendientes al inicio (%s): %d (%d del mes anterior); %d con plazo "
                        + "vencido antes del inicio quedan fuera del periodo simulado%n%n",
                formatear(t), pendientes, delMesAnterior, vencidos);
    }

    private void agregarPedidos(List<Pedido> pedidos) {
        for (Pedido p : pedidos) {
            pedidosVivos.add(p);
            siguienteId = Math.max(siguienteId, p.getId() + 1);
        }
        pedidosCargados += pedidos.size();
    }

    /**
     * Carga los meses siguientes mientras su inicio caiga dentro del horizonte. Si falta el
     * archivo de ventas de un mes, la simulación ya no tiene más datos.
     */
    private void cargarMesesHasta(int limite, int ahora) throws IOException {
        while (hayMasMeses && inicioMesSiguiente <= limite) {
            YearMonth mes = ultimoMesCargado.plusMonths(1);
            Path ventas = fuentes == null ? null : fuentes.ventas(mes);
            if (ventas == null) {
                hayMasMeses = false;
                salida.printf("    (no hay archivo de ventas para %s: la simulación termina al "
                        + "agotar los pedidos cargados)%n", mes);
                return;
            }
            CargadorVentas.Resultado rv = CargadorVentas.cargar(ventas, inicioMesSiguiente, siguienteId);
            agregarPedidos(rv.pedidos);
            Path bloqueos = fuentes.bloqueos(mes);
            int nBloqueos = 0;
            if (bloqueos != null) {
                CargadorBloqueos.Resultado rb = CargadorBloqueos.cargar(bloqueos, inicioMesSiguiente);
                instancia.getMapa().agregarBloqueos(rb.bloqueos);
                nBloqueos = rb.bloqueos.size();
            }
            instancia.getMapa().descartarBloqueosTerminadosAntesDe(ahora);
            salida.printf("    [%s] mes %s cargado: %d pedidos, %d bloqueos%n",
                    formatear(ahora), mes, rv.pedidos.size(), nBloqueos);
            ultimoMesCargado = mes;
            inicioMesSiguiente += mes.lengthOfMonth() * Turnos.MINUTOS_POR_DIA;
            resultado.mesesCargados++;
        }
    }

    // ------------------------------------------------------------------ avance

    /**
     * Despacha, viaje por viaje, lo que sale antes del siguiente ciclo. Un viaje despachado
     * descuenta su carga del almacén donde carga y compromete a la unidad hasta que llega al
     * almacén donde termina; sus paradas quedan en curso. Los viajes siguientes de la ruta no se
     * despachan todavía: siguen en el plan vigente y se replanifican en el próximo ciclo.
     *
     * @return número de rutas con al menos un viaje despachado
     */
    private int despachar(Solucion plan, int siguienteCiclo) {
        int despachadas = 0;
        for (Ruta r : plan.getRutas()) {
            if (r.estaVacia() || r.getMinutoInicio() >= siguienteCiclo) {
                continue;
            }
            Vehiculo v = r.getVehiculo();
            int[] llegadas = r.getMinutosLlegada();
            List<Ruta.Viaje> viajes = r.getViajes();
            int enviados = 0;
            for (Ruta.Viaje viaje : viajes) {
                if (enviados > 0 && viaje.getSalida() >= siguienteCiclo) {
                    break;
                }
                for (int i = viaje.getDesde(); i < viaje.getHasta(); i++) {
                    Pedido parte = r.getSecuencia().get(i);
                    parte.setUnidadAsignada(v.getCodigo());
                    parte.setMinutoEntregaEstimado(llegadas[i]);
                    if (parte.esFraccion()) {
                        parte.setEstado(Pedido.Estado.EN_RUTA);
                    }
                    parte.registrarDespacho(parte.getCantidad());
                    paradasEnCurso.add(new Parada(parte, llegadas[i]));
                }
                viaje.getAlmacenCarga().descontar(viaje.getCarga());
                resultado.viajesDespachados++;
                resultado.viajesPorTipo.merge(v.getTipo().name(), 1, Integer::sum);
                resultado.kmRecorridos += viaje.getDistanciaKm();
                resultado.costoSoles += viaje.getDistanciaKm() * v.getTipo().getCostoPorKm();
                enviados++;
            }
            Ruta.Viaje ultimo = viajes.get(enviados - 1);
            v.setPosicion(ultimo.getAlmacenFin().getUbicacion());
            v.setMinutoDisponibleDesde(ultimo.getFin());
            v.setEstado(Vehiculo.Estado.EN_RUTA);
            int viajeComida = r.viajeDeAlimentacion();
            if (viajeComida >= 0 && viajeComida < enviados) {
                v.setTurnoDeUltimaAlimentacion(Turnos.inicioTurno(r.getMinutoInicioAlimentacion()));
            }
            resultado.rutasDespachadas++;
            despachadas++;
        }
        return despachadas;
    }

    /**
     * Registra las entregas con llegada ≤ T y libera las unidades que ya regresaron.
     *
     * @return descripción de una entrega tardía, o {@code null} si todas llegaron a tiempo
     */
    private String registrarEntregas(int t) {
        String tardia = null;
        boolean completados = false;
        List<Parada> pendientes = new ArrayList<>(paradasEnCurso.size());
        for (Parada p : paradasEnCurso) {
            if (p.llegada > t) {
                pendientes.add(p);
                continue;
            }
            Pedido parte = p.parte;
            if (parte.esFraccion()) {
                parte.setEstado(Pedido.Estado.ENTREGADO);
                parte.setMinutoEntregaReal(p.llegada);
            }
            resultado.paquetesEntregados += parte.getCantidad();
            if (parte.registrarEntrega(parte.getCantidad(), p.llegada)) {
                resultado.pedidosEntregados++;
                int holgura = parte.getMinutoLimite() - parte.getOriginal().getMinutoEntregaReal();
                resultado.holguraEntregaTotalMin += holgura;
                resultado.holguraEntregaMinimaMin = Math.min(resultado.holguraEntregaMinimaMin, holgura);
                if (parte.getOriginal().getPartesDespachadas() > 1) {
                    resultado.pedidosFraccionados++;
                }
                completados = true;
            }
            if (p.llegada > parte.getMinutoLimite() && tardia == null) {
                tardia = "entrega tardía de " + parte + " a las " + formatear(p.llegada);
            }
        }
        paradasEnCurso.clear();
        paradasEnCurso.addAll(pendientes);
        if (completados) {
            pedidosVivos.removeIf(p -> p.getEstado() == Pedido.Estado.ENTREGADO);
        }
        for (Vehiculo v : instancia.getFlota()) {
            if (v.getEstado() == Vehiculo.Estado.EN_RUTA && v.getMinutoDisponibleDesde() <= t) {
                v.setEstado(Vehiculo.Estado.DISPONIBLE);
            }
        }
        return tardia;
    }

    /** Pedido registrado cuyo plazo venció en T sin haberse despachado por completo. */
    private String plazoVencido(int t) {
        for (Pedido p : pedidosVivos) {
            if (p.getMinutoRegistro() <= t && p.cantidadPendiente() > 0 && p.getMinutoLimite() < t) {
                return "plazo vencido sin despachar de " + p;
            }
        }
        return null;
    }

    private void colapsar(String causa) {
        resultado.fin = ResultadoSimulacion.Fin.COLAPSO;
        resultado.causaColapso = causa;
    }

    // ------------------------------------------------------------------ salida

    private LocalDateTime instante(int minuto) {
        return instancia.getFechaInicio().atStartOfDay().plusMinutes(minuto);
    }

    /** Instante simulado como fecha y hora de calendario. */
    public String formatear(int minuto) {
        LocalDateTime f = instante(minuto);
        return String.format("%04d-%02d-%02d %02d:%02d", f.getYear(), f.getMonthValue(),
                f.getDayOfMonth(), f.getHour(), f.getMinute());
    }

    private void imprimirDia(int dia, long nsDia, int ejecuciones) {
        if (par.detalle) {
            return;
        }
        int pendientes = 0;
        int finDia = dia * Turnos.MINUTOS_POR_DIA;
        for (Pedido p : pedidosVivos) {
            if (p.getMinutoRegistro() < finDia) {
                pendientes++;
            }
        }
        salida.printf(Locale.ROOT, "[%s] entregados=%d fraccionados=%d en curso/pendientes=%d "
                        + "rutas=%d km=%.0f · planificador %d ejec. %.2f s (Ta prom. %.1f ms)%n",
                formatear((dia - 1) * Turnos.MINUTOS_POR_DIA).substring(0, 10),
                resultado.pedidosEntregados, resultado.pedidosFraccionados, pendientes,
                resultado.rutasDespachadas, resultado.kmRecorridos, ejecuciones, nsDia / 1e9,
                ejecuciones == 0 ? 0 : nsDia / 1e6 / ejecuciones);
    }

    private BufferedWriter abrirCsvCiclos() throws IOException {
        if (par.csvCiclos == null) {
            return null;
        }
        if (par.csvCiclos.getParent() != null) {
            Files.createDirectories(par.csvCiclos.getParent());
        }
        BufferedWriter w = Files.newBufferedWriter(par.csvCiclos, StandardCharsets.UTF_8);
        w.write("ciclo,minuto,instante,pedidos_considerados,unidades_asignables,ta_ms,factible,"
                + "rutas_despachadas,costo_plan,pedidos_entregados_acum\n");
        return w;
    }

    private void escribirCiclo(BufferedWriter w, int t, ContextoPlanificacion ctx, long taNs,
                               boolean factible, int despachadas, double costoPlan) throws IOException {
        if (w == null) {
            return;
        }
        w.write(String.format(Locale.ROOT, "%d,%d,%s,%d,%d,%.3f,%s,%d,%.2f,%d%n",
                resultado.ciclos + 1, t, formatear(t), ctx.getPedidosPorAtender().size(),
                ctx.getUnidadesAsignables().size(), taNs / 1e6, factible, despachadas, costoPlan,
                resultado.pedidosEntregados));
    }

    private static List<String> primeros(List<String> lista, int n) {
        return lista.subList(0, Math.min(n, lista.size()));
    }
}
