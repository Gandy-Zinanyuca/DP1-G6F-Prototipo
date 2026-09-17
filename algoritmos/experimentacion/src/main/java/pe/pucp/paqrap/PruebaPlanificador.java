package pe.pucp.paqrap;

import pe.pucp.paqrap.alns.ALNS;
import pe.pucp.paqrap.servicios.ConstructorInicial;
import pe.pucp.paqrap.alns.ParametrosALNS;
import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Banco de pruebas de verificación del componente planificador.
 *
 * <p>Ejecuta cinco comprobaciones sobre un ciclo real construido a partir de los archivos del
 * curso. No sustituye a un conjunto de pruebas unitarias, pero cubre las propiedades que, si se
 * rompen, invalidan cualquier resultado de la experimentación numérica:</p>
 *
 * <ol>
 *   <li><b>Aporte del metaheurístico</b>: el ALNS debe mejorar la solución de la heurística
 *       constructiva. Si no la mejora, no hay razón para usarlo.</li>
 *   <li><b>Reproducibilidad</b>: dos ejecuciones con la misma semilla deben dar el mismo costo
 *       (LE008, LE009).</li>
 *   <li><b>Integridad de la asignación</b>: cada pedido aparece exactamente una vez, sea en una
 *       ruta o en la lista de diferidos; ninguno se duplica ni se pierde (LE006).</li>
 *   <li><b>Capacidad</b>: ninguna ruta excede la capacidad de su unidad (LE014, LE027).</li>
 *   <li><b>Inventario</b>: ningún almacén intermedio queda con stock negativo (LE019).</li>
 * </ol>
 *
 * <h2>Uso</h2>
 * <pre>
 *   java -cp out pe.pucp.paqrap.PruebaPlanificador &lt;ventas.txt&gt; [bloqueos.txt] [--dia N] [--hora N]
 * </pre>
 */
public final class PruebaPlanificador {

    private static int fallos = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java pe.pucp.paqrap.PruebaPlanificador <ventas.txt> "
                    + "[bloqueos.txt] [--dia N] [--hora N]");
            return;
        }
        Path ventas = Paths.get(args[0]);
        Path bloqueos = null;
        int dia = 1;
        int hora = 8;
        for (int i = 1; i < args.length; i++) {
            if ("--dia".equals(args[i])) {
                dia = Integer.parseInt(args[++i]);
            } else if ("--hora".equals(args[i])) {
                hora = Integer.parseInt(args[++i]);
            } else if (bloqueos == null) {
                Path p = Paths.get(args[i]);
                bloqueos = Files.exists(p) ? p : null;
            }
        }

        Instancia instancia = Instancia.construir(ventas, bloqueos, null, 2026, 1,
                Almacen.CAPACIDAD_INTERMEDIO_POR_DEFECTO,
                Instancia.AUTOS_POR_DEFECTO, Instancia.MOTOS_POR_DEFECTO,
                Instancia.BICICLETAS_POR_DEFECTO);

        ParametrosPlanificador parPlan = new ParametrosPlanificador();
        int minuto = Turnos.aMinutos(dia, hora, 0);
        ContextoPlanificacion ctx = ContextoPlanificacion.construir(
                instancia, minuto, instancia.getPedidos(), Collections.emptyList(), parPlan);

        System.out.println("=====================================================================");
        System.out.println(" Verificación del componente planificador");
        System.out.println("=====================================================================");
        System.out.printf(" Instante: %s · pedidos: %d · unidades: %d · bloqueos vigentes: %d%n%n",
                Turnos.formatear(minuto), ctx.getPedidosPorAtender().size(),
                ctx.getUnidadesAsignables().size(), ctx.getMapa().getBloqueosVigentes().size());

        if (ctx.getPedidosPorAtender().isEmpty()) {
            System.out.println(" No hay pedidos en el horizonte; elija otro día u hora.");
            return;
        }

        // --- 1. Aporte del metaheurístico frente a la heurística constructiva -------------
        Solucion golosa = ConstructorInicial.construir(ctx);
        double fGolosa = golosa.evaluar(ctx);

        ParametrosALNS parAlns = new ParametrosALNS();
        parAlns.presupuestoMs = 5_000;
        parAlns.maxIteraciones = 6_000;
        ALNS alns = new ALNS(parAlns);
        Solucion mejorada = alns.resolver(ctx);
        double fAlns = mejorada.evaluar(ctx);

        System.out.printf(" [1] Heurística constructiva : f = %,.2f  (km %.0f · S/ %.2f · "
                        + "tardíos %d · diferidos %d)%n",
                fGolosa, golosa.getDistanciaTotalKm(), golosa.getCostoOperacionSoles(),
                golosa.getPedidosTardios(), golosa.getNoAsignados().size());
        System.out.printf("     ALNS                    : f = %,.2f  (km %.0f · S/ %.2f · "
                        + "tardíos %d · diferidos %d)%n",
                fAlns, mejorada.getDistanciaTotalKm(), mejorada.getCostoOperacionSoles(),
                mejorada.getPedidosTardios(), mejorada.getNoAsignados().size());
        System.out.printf("     %d iteraciones en %d ms%n",
                alns.getEstadisticas().iteraciones, alns.getEstadisticas().milisegundos);
        verificar("ALNS no empeora la solución constructiva", fAlns <= fGolosa + 1e-6);

        // --- 2. Reproducibilidad ----------------------------------------------------------
        ALNS repetido = new ALNS(parAlns);
        double fRepetido = repetido.resolver(ctx).evaluar(ctx);
        System.out.printf("%n [2] Repetición con la misma semilla: f = %,.2f%n", fRepetido);
        verificar("Ejecuciones con igual semilla dan igual costo (LE008)",
                Math.abs(fRepetido - fAlns) < 1e-6);

        // --- 3. Integridad de la asignación ----------------------------------------------
        Set<Integer> vistos = new HashSet<>();
        boolean duplicados = false;
        for (Ruta r : mejorada.getRutas()) {
            for (Pedido p : r.getSecuencia()) {
                if (!vistos.add(p.getId())) {
                    duplicados = true;
                }
            }
        }
        for (Pedido p : mejorada.getNoAsignados()) {
            if (!vistos.add(p.getId())) {
                duplicados = true;
            }
        }
        System.out.printf("%n [3] Pedidos del ciclo: %d · referenciados en la solución: %d%n",
                ctx.getPedidosPorAtender().size(), vistos.size());
        verificar("Ningún pedido duplicado", !duplicados);
        verificar("Ningún pedido perdido (LE006)",
                vistos.size() == ctx.getPedidosPorAtender().size());

        // --- 4. Capacidad de las unidades -------------------------------------------------
        boolean capacidadOk = true;
        for (Ruta r : mejorada.getRutas()) {
            if (r.getCargaTotal() > r.getVehiculo().getCapacidad()) {
                capacidadOk = false;
                System.out.println("     ! " + r.getVehiculo().getCodigo() + " lleva "
                        + r.getCargaTotal() + " > " + r.getVehiculo().getCapacidad());
            }
        }
        System.out.println();
        verificar("Ninguna ruta excede la capacidad de su unidad (LE014, LE027)", capacidadOk);

        // --- 5. Inventario de los almacenes intermedios -----------------------------------
        boolean inventarioOk = true;
        for (Almacen a : ctx.getAlmacenes()) {
            if (a.esCentral()) {
                continue;
            }
            int restante = ctx.stockInicial(a) - mejorada.consumo(a);
            System.out.printf("     %s: consumo %d de %d → restante %d%n",
                    a.getId(), mejorada.consumo(a), ctx.stockInicial(a), restante);
            if (restante < 0) {
                inventarioOk = false;
            }
        }
        verificar("Ningún almacén intermedio queda con stock negativo (LE019)", inventarioOk);

        System.out.println("=====================================================================");
        System.out.println(fallos == 0
                ? " TODAS LAS VERIFICACIONES PASARON"
                : " VERIFICACIONES FALLIDAS: " + fallos);
        System.out.println("=====================================================================");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void verificar(String descripcion, boolean condicion) {
        System.out.println((condicion ? "     [OK]   " : "     [FALLA]") + " " + descripcion);
        if (!condicion) {
            fallos++;
        }
    }

    private PruebaPlanificador() {
    }
}
