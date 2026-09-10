package pe.pucp.paqrap;

import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.planificador.Planificador;
import pe.pucp.paqrap.planificador.PlanificadorALNS;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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
 *                                                [--dia N] [--ciclos N] [--semilla N] [--traza]
 * </pre>
 */
public final class DemoPlanificador {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java pe.pucp.paqrap.DemoPlanificador <ventas.txt> "
                    + "[bloqueos.txt] [mantenimiento.txt] [--dia N] [--hora N] [--ciclos N] "
                    + "[--horizonte H] [--semilla N] [--traza]");
            return;
        }

        Path ventas = Paths.get(args[0]);
        Path bloqueos = null;
        Path mantenimiento = null;
        int diaInicial = 1;
        int horaInicial = 7;
        int ciclos = 8;
        int horizonteHoras = 24;
        long semilla = 20262L;
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
                case "--semilla":
                    semilla = Long.parseLong(args[++i]);
                    break;
                case "--traza":
                    traza = true;
                    break;
                case "--horizonte":
                    horizonteHoras = Integer.parseInt(args[++i]);
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

        // ---------------------------------------------------------------- carga
        Instancia instancia = Instancia.construir(
                ventas, bloqueos, mantenimiento,
                2026, 1,
                Almacen.CAPACIDAD_INTERMEDIO_POR_DEFECTO,
                Instancia.AUTOS_POR_DEFECTO,
                Instancia.MOTOS_POR_DEFECTO,
                Instancia.BICICLETAS_POR_DEFECTO);

        System.out.println("=====================================================================");
        System.out.println(" PaqRap · Componente planificador · ALNS");
        System.out.println("=====================================================================");
        System.out.println(instancia);
        System.out.println("Almacenes: " + instancia.getAlmacenes());
        System.out.println();

        ParametrosPlanificador parPlan = new ParametrosPlanificador();
        parPlan.horizonteAtencionMinutos = horizonteHoras * 60;
        PlanificadorALNS planificador = PlanificadorALNS.paraOperacionDiaria();
        planificador.getParametros().traza = traza;
        planificador.reiniciarMotor(semilla);

        List<Pedido> pedidosVivos = new ArrayList<>(instancia.getPedidos());
        Solucion planPrevio = null;

        int minuto = Turnos.aMinutos(diaInicial, horaInicial, 0);
        int entregadosAcumulados = 0;
        double kmAcumulados = 0;
        double solesAcumulados = 0;
        int incumplidos = 0;

        for (int c = 0; c < ciclos; c++) {
            liberarUnidadesQueRegresaron(instancia, minuto);

            // En este arnés las rutas se despachan completas, de modo que una unidad en ruta no
            // vuelve a recibir carga hasta regresar. El simulador definitivo sí podrá añadirle
            // pedidos en camino, que es la reasignación que describe el enunciado.
            List<String> ocupadas = new ArrayList<>();
            for (Vehiculo v : instancia.getFlota()) {
                if (v.getEstado() == Vehiculo.Estado.EN_RUTA) {
                    ocupadas.add(v.getCodigo());
                }
            }

            ContextoPlanificacion ctx = ContextoPlanificacion.construir(
                    instancia, minuto, pedidosVivos, ocupadas, parPlan);

            System.out.printf("--- Ciclo %d · %s · pedidos por atender: %d · unidades: %d · "
                            + "bloqueos vigentes: %d%n",
                    c + 1, Turnos.formatear(minuto),
                    ctx.getPedidosPorAtender().size(),
                    ctx.getUnidadesAsignables().size(),
                    ctx.getMapa().getBloqueosVigentes().size());

            if (ctx.getPedidosPorAtender().isEmpty()) {
                minuto += parPlan.minutosPorCicloPlanificacion;
                System.out.println("    (sin pedidos en el horizonte de atención)");
                continue;
            }

            Solucion plan = planificador.planificar(ctx, planPrevio);
            System.out.print(planificador.resumenUltimaEjecucion());

            Resumen r = ejecutarPlan(plan, ctx);
            entregadosAcumulados += r.entregados;
            kmAcumulados += plan.getDistanciaTotalKm();
            solesAcumulados += plan.getCostoOperacionSoles();
            incumplidos += r.incumplidos;

            System.out.printf("    asignados=%d en %d rutas · km=%.0f · S/ %.2f · "
                            + "diferidos=%d · fuera de plazo=%d%n",
                    r.entregados, plan.numeroUnidadesUsadas(), plan.getDistanciaTotalKm(),
                    plan.getCostoOperacionSoles(), plan.getNoAsignados().size(), r.incumplidos);

            if (c == 0) {
                System.out.println();
                System.out.println("    Asignación de rutas del primer ciclo:");
                imprimirAsignacion(plan);
                System.out.println();
            }

            planPrevio = null;   // las rutas se ejecutaron completas en este arnés
            minuto += parPlan.minutosPorCicloPlanificacion;

            // Recarga diaria de los almacenes intermedios a las 23:59:59 (LE033).
            if (Turnos.minutoDelDia(minuto) < parPlan.minutosPorCicloPlanificacion) {
                for (Almacen a : instancia.getAlmacenes()) {
                    a.recargar();
                }
            }
        }

        System.out.println("=====================================================================");
        System.out.printf(" Resumen: %d pedidos entregados · %.0f km · S/ %.2f · %d fuera de plazo%n",
                entregadosAcumulados, kmAcumulados, solesAcumulados, incumplidos);
        System.out.println("=====================================================================");
    }

    /** Contadores del avance de un ciclo. */
    private static class Resumen {
        int entregados;
        int incumplidos;
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
                if (llegadas[i] > p.getMinutoLimite()) {
                    p.setEstado(Pedido.Estado.NO_CUMPLIDO);
                    resumen.incumplidos++;
                } else {
                    p.setEstado(Pedido.Estado.ENTREGADO);
                    resumen.entregados++;
                }
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
        if (!plan.getNoAsignados().isEmpty()) {
            System.out.println("    Diferidos al siguiente ciclo: " + plan.getNoAsignados().size());
        }
    }

    private DemoPlanificador() {
    }
}
