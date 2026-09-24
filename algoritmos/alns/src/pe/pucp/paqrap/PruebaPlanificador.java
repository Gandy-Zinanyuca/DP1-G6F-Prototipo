package pe.pucp.paqrap;

import pe.pucp.paqrap.alns.ALNS;
import pe.pucp.paqrap.alns.ConstructorInicial;
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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Banco de pruebas de verificación del componente planificador ALNS (ISA 5.2).
 *
 * <ol>
 * <li><b>Factibilidad</b>: la solución devuelta cumple todas las restricciones
 * duras, o se reporta como no factible (colapso) sin iterar.</li>
 * <li><b>Aporte del metaheurístico</b>: el costo de ALNS no supera al de la
 * solución inicial.</li>
 * <li><b>Métricas</b>: candidatosEvaluados = maxIteraciones = factibles +
 * noFactibles.</li>
 * <li><b>Operadores</b>: 5 de destrucción y 2 de reparación.</li>
 * <li><b>Reproducibilidad</b>: dos ejecuciones con la misma semilla dan el
 * mismo costo (LE008).</li>
 * <li><b>Integridad</b>: ninguna parte aparece dos veces y la cantidad de cada
 * pedido considerado queda cubierta exactamente, completa o repartida entre
 * unidades (LE006).</li>
 * <li><b>Capacidad, plazos e inventario</b> verificados de forma
 * independiente.</li>
 * </ol>
 *
 * <h2>Uso</h2>
 *
 * <pre>
 *   java -cp out pe.pucp.paqrap.PruebaPlanificador &lt;ventas.txt&gt; [bloqueos.txt] [--dia N] [--hora N]
 *                                                  [--iteraciones N]
 * </pre>
 */
public final class PruebaPlanificador {

    private static int fallos = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java pe.pucp.paqrap.PruebaPlanificador <ventas.txt> "
                    + "[bloqueos.txt] [--dia N] [--hora N] [--iteraciones N]");
            return;
        }
        Path ventas = Paths.get(args[0]);
        Path bloqueos = null;
        int dia = 1;
        int hora = 8;
        int iteraciones = 500;
        for (int i = 1; i < args.length; i++) {
            if ("--dia".equals(args[i])) {
                dia = Integer.parseInt(args[++i]);
            } else if ("--hora".equals(args[i])) {
                hora = Integer.parseInt(args[++i]);
            } else if ("--iteraciones".equals(args[i])) {
                iteraciones = Integer.parseInt(args[++i]);
            } else if (bloqueos == null) {
                Path p = Paths.get(args[i]);
                bloqueos = Files.exists(p) ? p : null;
            }
        }

        Instancia instancia = Instancia.construir(ventas, bloqueos, null, 2026, 1,
                Almacen.CAPACIDAD_INTERMEDIO_POR_DEFECTO, Instancia.AUTOS_POR_DEFECTO, Instancia.MOTOS_POR_DEFECTO,
                Instancia.BICICLETAS_POR_DEFECTO);

        ParametrosALNS parAlns = new ParametrosALNS();
        parAlns.maxIteraciones = iteraciones;

        ParametrosPlanificador parPlan = new ParametrosPlanificador();
        int minuto = Turnos.aMinutos(dia, hora, 0);
        // Sin pedidos entregados previamente: se consideran los registrados hasta T +
        // Sc.
        ContextoPlanificacion ctx = ContextoPlanificacion.construir(instancia, minuto, (int) parAlns.scMinutos(),
                instancia.getPedidos(), Collections.emptyList(), parPlan);

        System.out.println("=====================================================================");
        System.out.println(" Verificación del componente planificador (ALNS)");
        System.out.println("=====================================================================");
        System.out.printf(" Instante: %s · Sc=%d min · pedidos: %d · unidades: %d · bloqueos vigentes: %d%n%n",
                Turnos.formatear(minuto), parAlns.scMinutos(), ctx.getPedidosPorAtender().size(),
                ctx.getUnidadesAsignables().size(), ctx.getMapa().getBloqueosVigentes().size());

        if (ctx.getPedidosPorAtender().isEmpty()) {
            System.out.println(" No hay pedidos en la ventana; elija otro día u hora.");
            return;
        }

        Solucion inicial = ConstructorInicial.construir(ctx);
        double costoInicial = inicial.evaluar(ctx);

        ALNS alns = new ALNS(parAlns);
        Solucion resultado = alns.resolver(ctx);
        ALNS.Estadisticas est = alns.getEstadisticas();
        System.out.print(est);

        // --- 1. Factibilidad
        // --------------------------------------------------------------
        if (!inicial.esFactible()) {
            System.out.println("\n [1] Solución inicial NO FACTIBLE (colapso): " + inicial.getErrores().get(0));
            verificar("ALNS devuelve resultado no factible sin iterar",
                    !resultado.esFactible() && est.iteraciones == 0);
            terminar();
            return;
        }
        System.out.printf("%n [1] Solución inicial: S/ %.2f · ALNS: S/ %.2f%n", costoInicial, resultado.getCosto());
        verificar("La solución de ALNS es factible", resultado.esFactible());

        // --- 2. Aporte
        // ---------------------------------------------------------------------
        verificar("ALNS no empeora la solución inicial", resultado.getCosto() <= costoInicial + 1e-6);

        // --- 3. Métricas
        // -------------------------------------------------------------------
        verificar("candidatosEvaluados = maxIteraciones", est.candidatosEvaluados == parAlns.maxIteraciones);
        verificar("candidatosEvaluados = factibles + noFactibles",
                est.candidatosEvaluados == est.candidatosFactibles + est.candidatosNoFactibles);

        // --- 4. Operadores
        // -----------------------------------------------------------------
        verificar("5 operadores de destrucción y 2 de reparación",
                est.nombresDestruccion.length == 5 && est.nombresReparacion.length == 2);

        // --- 5. Reproducibilidad
        // -----------------------------------------------------------
        double repetido = new ALNS(parAlns).resolver(ctx).getCosto();
        verificar("Ejecuciones con igual semilla dan igual costo (LE008)",
                Math.abs(repetido - resultado.getCosto()) < 1e-6);

        // --- 6. Integridad
        // -----------------------------------------------------------------
        Set<Pedido> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Pedido, Integer> cubierto = new IdentityHashMap<>();
        boolean duplicados = false;
        int fraccionados = 0;
        for (Ruta r : resultado.getRutas()) {
            for (Pedido p : r.getSecuencia()) {
                duplicados |= !vistos.add(p);
                cubierto.merge(p.getOriginal(), p.getCantidad(), Integer::sum);
            }
        }
        // Cobertura: lo asignado más lo reprogramado cubre cada pedido, y solo se
        // reprograma lo
        // que todavía tiene holgura para un ciclo posterior.
        Map<Pedido, Integer> reprogramado = new IdentityHashMap<>();
        boolean reprogramacionValida = true;
        for (Pedido p : resultado.getNoAsignados()) {
            reprogramado.merge(p.getOriginal(), p.getCantidad(), Integer::sum);
            reprogramacionValida &= ctx.esPostergable(p);
        }
        boolean cubiertos = true;
        for (Pedido p : ctx.getPedidosPorAtender()) {
            cubiertos &= cubierto.getOrDefault(p.getOriginal(), 0) + reprogramado.getOrDefault(p.getOriginal(), 0) == p
                    .getCantidad();
        }
        for (Pedido p : vistos) {
            if (p.esFraccion()) {
                fraccionados++;
            }
        }
        System.out.printf(
                " [6] Partes asignadas: %d (%d fracciones de pedidos repartidos) · reprogramados:"
                        + " %d pedidos / %d paquetes%n",
                vistos.size(), fraccionados, resultado.getPedidosPostergados(), resultado.getPaquetesPostergados());
        boolean mismaHora = true;
        for (Pedido p : vistos) {
            Pedido o = p.getOriginal();
            mismaHora &= p.getMinutoLimite() == o.getMinutoLimite() && p.getMinutoRegistro() == o.getMinutoRegistro()
                    && p.getDestino().equals(o.getDestino());
        }
        verificar("Ninguna parte de pedido duplicada", !duplicados);
        verificar("Cada fracción conserva el registro, destino y hora límite de su pedido", mismaHora);
        verificar("La cantidad de cada pedido considerado queda cubierta exactamente, asignada o"
                + " reprogramada (LE006)", cubiertos);
        verificar("Solo se reprograman pedidos con holgura suficiente", reprogramacionValida);

        // --- 7. Capacidad, plazos e inventario
        // ---------------------------------------------
        boolean capacidadOk = true;
        boolean plazosOk = true;
        int viajes = 0;
        int recargas = 0;
        for (Ruta r : resultado.getRutas()) {
            if (r.estaVacia()) {
                continue;
            }
            int cargaViajes = 0;
            for (Ruta.Viaje v : r.getViajes()) {
                int carga = 0;
                for (int i = v.getDesde(); i < v.getHasta(); i++) {
                    carga += r.getSecuencia().get(i).getCantidad();
                }
                capacidadOk &= carga == v.getCarga() && carga <= r.getVehiculo().getCapacidad();
                cargaViajes += carga;
            }
            capacidadOk &= cargaViajes == r.getCargaTotal();
            viajes += r.getViajes().size();
            recargas += r.getViajes().size() - 1;
            for (int i = 0; i < r.tamanio(); i++) {
                plazosOk &= r.minutoLlegada(i) <= r.getSecuencia().get(i).getMinutoLimite();
            }
        }
        System.out.printf(" [7] Viajes: %d (%d con recarga en ruta)%n", viajes, recargas);
        verificar("Ningún viaje excede la capacidad de su unidad y los viajes cubren la ruta (LE014, LE027)",
                capacidadOk);
        verificar("Ninguna entrega fuera de plazo (LE021)", plazosOk);
        boolean inventarioOk = true;
        for (Almacen a : ctx.getAlmacenes()) {
            if (!a.esCentral()) {
                inventarioOk &= resultado.consumo(ctx, a) <= ctx.stockInicial(a);
            }
        }
        verificar("Ningún almacén intermedio queda con stock negativo (LE019)", inventarioOk);
        terminar();
    }

    private static void terminar() {
        System.out.println("=====================================================================");
        System.out.println(fallos == 0 ? " TODAS LAS VERIFICACIONES PASARON" : " VERIFICACIONES FALLIDAS: " + fallos);
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
