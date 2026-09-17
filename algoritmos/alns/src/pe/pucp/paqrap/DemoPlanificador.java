package pe.pucp.paqrap;

import pe.pucp.paqrap.alns.ParametrosALNS;
import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.planificador.PlanificadorALNS;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Programa de prueba del componente planificador.
 *
 * <p>No es el simulador del proyecto: es el arnés mínimo que permite ejecutar el algoritmo
 * sobre los archivos reales de ventas y bloqueos, verificar que las asignaciones de rutas
 * cumplen las restricciones y medir el desempeño. El simulador completo —reloj continuo,
 * generación de averías, visualizador— se construye sobre estas mismas clases.</p>
 *
 * <p>El avance temporal que aplica es deliberadamente simple: cada ciclo planifica, <b>ejecuta
 * completas</b> las rutas asignadas (marca los pedidos como entregados, deja cada unidad en su
 * almacén de retorno y descuenta el inventario) y salta al siguiente ciclo. Basta para mostrar
 * la reoptimización entre ciclos y para validar la asignación de rutas, que es el alcance de
 * esta entrega.</p>
 *
 * <h2>Uso</h2>
 * <pre>
 *   java -cp out pe.pucp.paqrap.DemoPlanificador &lt;ventas.txt&gt; [bloqueos.txt] [mantenimiento.txt]
 *                                                [--dia N] [--hora N] [--ciclos N] [--sa MIN] [--k N]
 *                                                [--iteraciones N] [--semilla N] [--anio AAAA] [--mes MM]
 *                                                [--traza]
 * </pre>
 *
 * <p>El año y el mes se deducen del nombre del archivo de ventas ({@code ventas.AAAAMM.txt}); con
 * {@code --anio} y {@code --mes} se pueden forzar. Determinan qué mantenimientos aplican.</p>
 * <pre>
 * </pre>
 */
public final class DemoPlanificador {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java pe.pucp.paqrap.DemoPlanificador <ventas.txt> "
                    + "[bloqueos.txt] [mantenimiento.txt] [--dia N] [--hora N] [--ciclos N] "
                    + "[--sa MIN] [--k N] [--iteraciones N] [--semilla N] [--anio AAAA] [--mes MM] [--traza]");
            return;
        }

        Path ventas = Paths.get(args[0]);
        Path bloqueos = null;
        Path mantenimiento = null;
        int diaInicial = 1;
        int horaInicial = 7;
        int ciclos = 8;
        Long saMinutos = null;
        Integer k = null;
        Integer iteraciones = null;
        long semilla = 20262L;
        Integer anio = null;
        Integer mes = null;
        boolean traza = false;

        List<String> posicionales = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--dia":
                    diaInicial = Integer.parseInt(args[++i]);
                    break;
                case "--ciclos":
                    ciclos = Integer.parseInt(args[++i]);
                    break;
                case "--anio":
                    anio = Integer.parseInt(args[++i]);
                    break;
                case "--mes":
                    mes = Integer.parseInt(args[++i]);
                    break;
                case "--semilla":
                    semilla = Long.parseLong(args[++i]);
                    break;
                case "--traza":
                    traza = true;
                    break;
                case "--sa":
                    saMinutos = Long.parseLong(args[++i]);
                    break;
                case "--k":
                    k = Integer.parseInt(args[++i]);
                    break;
                case "--iteraciones":
                    iteraciones = Integer.parseInt(args[++i]);
                    break;
                case "--hora":
                    horaInicial = Integer.parseInt(args[++i]);
                    break;
                default:
                    posicionales.add(args[i]);
            }
        }
        if (!posicionales.isEmpty()) {
            bloqueos = Paths.get(posicionales.get(0));
        }
        if (posicionales.size() > 1) {
            mantenimiento = Paths.get(posicionales.get(1));
        }
        if (bloqueos != null && !Files.exists(bloqueos)) {
            bloqueos = null;
        }
        if (mantenimiento != null && !Files.exists(mantenimiento)) {
            mantenimiento = null;
        }

        // Año y mes del archivo de ventas (ventas.AAAAMM.txt), salvo que se indiquen explícitamente.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})(\\d{2})")
                .matcher(ventas.getFileName().toString());
        if (m.find()) {
            if (anio == null) {
                anio = Integer.parseInt(m.group(1));
            }
            if (mes == null) {
                mes = Integer.parseInt(m.group(2));
            }
        }
        if (anio == null || mes == null) {
            System.out.println("No se pudo deducir el año y mes del archivo de ventas; use --anio y --mes.");
            return;
        }

        // ---------------------------------------------------------------- carga
        Instancia instancia = Instancia.construir(
                ventas, bloqueos, mantenimiento,
                anio, mes,
                Almacen.CAPACIDAD_INTERMEDIO_POR_DEFECTO,
                Instancia.AUTOS_POR_DEFECTO,
                Instancia.MOTOS_POR_DEFECTO,
                Instancia.BICICLETAS_POR_DEFECTO);

        System.out.println("=====================================================================");
        System.out.println(" PaqRap · Componente planificador · ALNS");
        System.out.println("=====================================================================");
        System.out.printf("Periodo simulado: %04d-%02d%n", anio, mes);
        System.out.println(instancia);
        System.out.println("Almacenes: " + instancia.getAlmacenes());
        System.out.println();

        ParametrosPlanificador parPlan = new ParametrosPlanificador();
        PlanificadorALNS planificador = PlanificadorALNS.paraOperacionDiaria();
        ParametrosALNS parAlns = planificador.getParametros();
        parAlns.traza = traza;
        if (saMinutos != null) {
            parAlns.saMinutos = saMinutos;
        }
        if (k != null) {
            parAlns.k = k;
        }
        if (iteraciones != null) {
            parAlns.maxIteraciones = iteraciones;
        }
        planificador.reiniciarMotor(semilla);
        int sa = (int) parAlns.saMinutos;
        int sc = (int) parAlns.scMinutos();
        System.out.printf("Sa=%d min · K=%d · Sc=%d min · maxIteraciones=%d%n%n",
                sa, parAlns.k, sc, parAlns.maxIteraciones);

        List<Pedido> pedidosVivos = new ArrayList<>(instancia.getPedidos());

        int minuto = Turnos.aMinutos(diaInicial, horaInicial, 0);
        int entregadosAcumulados = 0;
        double kmAcumulados = 0;
        double solesAcumulados = 0;
        boolean colapso = false;
        Solucion planVigente = null;

        for (int c = 0; c < ciclos; c++) {
            liberarUnidadesQueRegresaron(instancia, minuto);

            // Una unidad en ruta sigue siendo asignable: su nueva ruta parte del almacén de retorno
            // a partir de su hora de regreso (momento = máx(T, disponibilidad), ISA 5.1). En este
            // arnés no se generan averías, así que ninguna unidad queda no disponible.
            List<String> noDisponibles = new ArrayList<>();

            ContextoPlanificacion ctx = ContextoPlanificacion.construir(
                    instancia, minuto, sc, pedidosVivos, noDisponibles, parPlan);

            System.out.printf("--- Ciclo %d · %s · pedidos por atender: %d · unidades: %d · "
                            + "bloqueos vigentes: %d%n",
                    c + 1, Turnos.formatear(minuto),
                    ctx.getPedidosPorAtender().size(),
                    ctx.getUnidadesAsignables().size(),
                    ctx.getMapa().getBloqueosVigentes().size());

            if (ctx.getPedidosPorAtender().isEmpty()) {
                minuto += sa;
                System.out.println("    (sin pedidos en la ventana de consumo)");
                continue;
            }

            Solucion plan = planificador.planificar(ctx, planVigente);
            System.out.print(planificador.resumenUltimaEjecucion());

            if (!plan.esFactible()) {
                System.out.println("    COLAPSO LOGÍSTICO: no existe plan factible en "
                        + Turnos.formatear(minuto));
                colapso = true;
                break;
            }

            planVigente = plan.copia();
            Resumen r = ejecutarPlan(plan, ctx);
            entregadosAcumulados += r.entregados;
            kmAcumulados += plan.getDistanciaTotalKm();
            solesAcumulados += plan.getCostoOperacionSoles();

            System.out.printf("    asignados=%d en %d rutas · km=%.0f · S/ %.2f%n",
                    r.entregados, plan.numeroUnidadesUsadas(), plan.getDistanciaTotalKm(),
                    plan.getCostoOperacionSoles());

            if (c == 0) {
                System.out.println();
                System.out.println("    Asignación de rutas del primer ciclo:");
                imprimirAsignacion(plan);
                System.out.println();
            }

            minuto += sa;

            // Recarga diaria de los almacenes intermedios a las 23:59:59 (LE033).
            if (Turnos.minutoDelDia(minuto) < sa) {
                for (Almacen a : instancia.getAlmacenes()) {
                    a.recargar();
                }
            }
        }

        System.out.println("=====================================================================");
        System.out.printf(" Resumen: %d pedidos entregados · %.0f km · S/ %.2f%s%n",
                entregadosAcumulados, kmAcumulados, solesAcumulados, colapso ? " · COLAPSO" : "");
        System.out.println("=====================================================================");
    }

    /** Contadores del avance de un ciclo. */
    private static class Resumen {
        int entregados;
    }

    /**
     * Ejecuta el plan: da por entregados los pedidos de cada ruta, mueve las unidades a su
     * almacén de retorno y descuenta el inventario consumido (LE032).
     */
    private static Resumen ejecutarPlan(Solucion plan, ContextoPlanificacion ctx) {
        Resumen resumen = new Resumen();
        for (Ruta r : plan.getRutas()) {
            if (r.estaVacia()) {
                continue;
            }
            Vehiculo v = r.getVehiculo();
            int[] llegadas = r.getMinutosLlegada();

            for (int i = 0; i < r.tamanio(); i++) {
                Pedido p = r.getSecuencia().get(i);
                p.setUnidadAsignada(v.getCodigo());
                p.setMinutoEntregaEstimado(llegadas[i]);
                p.setMinutoEntregaReal(llegadas[i]);
                p.setEstado(Pedido.Estado.ENTREGADO);
                resumen.entregados++;
                r.getAlmacenOrigen().descontar(p.getCantidad());
            }
            v.setPosicion(r.getAlmacenRetorno().getUbicacion());
            v.setMinutoDisponibleDesde(r.getMinutoRetorno());
            v.setEstado(Vehiculo.Estado.EN_RUTA);
            if (r.getMinutoInicioAlimentacion() >= 0) {
                v.setTurnoDeUltimaAlimentacion(Turnos.inicioTurno(r.getMinutoInicioAlimentacion()));
            }
        }
        return resumen;
    }

    /**
     * Devuelve al estado disponible las unidades que ya completaron su recorrido. En el
     * simulador definitivo esto lo hace el avance del reloj; aquí basta con comparar el minuto
     * de retorno registrado al despachar la ruta.
     */
    private static void liberarUnidadesQueRegresaron(Instancia instancia, int minuto) {
        for (Vehiculo v : instancia.getFlota()) {
            if (v.getEstado() == Vehiculo.Estado.EN_RUTA
                    && v.getMinutoDisponibleDesde() <= minuto) {
                v.setEstado(Vehiculo.Estado.DISPONIBLE);
            }
        }
    }

    /** Imprime la asignación de rutas tal como la consumiría el visualizador. */
    private static void imprimirAsignacion(Solucion plan) {
        for (Ruta r : plan.getRutas()) {
            if (r.estaVacia()) {
                continue;
            }
            System.out.println("    " + r.toString().replace("\n", "\n    "));
        }
    }

    private DemoPlanificador() {
    }
}
