package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * <b>Operador propio del dominio</b>: vehicle-failure removal (ISA 5.2).
 *
 * <pre>
 * vehículosAveriados ← vehículos con mantenimiento vigente para el instante T
 * PARA CADA ruta de parcial cuyo vehículo esté en vehículosAveriados
 *     retirar todos los pedidos de esa ruta
 * </pre>
 *
 * <p>Además del mantenimiento programado, se consideran averiadas las unidades que dejaron de
 * estar disponibles en T (fuera del conjunto de unidades asignables). El operador no usa el
 * grado de destrucción: retira todos los pedidos afectados.</p>
 *
 * <p>Es aplicable solo si la solución tiene rutas con pedidos en unidades averiadas o en
 * mantenimiento. La reparación nunca asigna a esas unidades, así que si la solución inicial no
 * tiene ninguna, el operador no puede remover nada en toda la ejecución y ALNS no lo sortea.</p>
 */
public class RemocionPorAveria implements OperadorDestruccion {

    @Override
    public boolean aplicable(Solucion solucion, ContextoPlanificacion ctx) {
        Set<String> disponibles = disponibles(ctx);
        for (Ruta r : solucion.getRutas()) {
            if (!r.estaVacia() && averiado(r, disponibles, ctx)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        Set<String> disponibles = disponibles(ctx);

        List<Pedido> removidos = new ArrayList<>();
        for (Ruta r : solucion.getRutas()) {
            if (!averiado(r, disponibles, ctx)) {
                continue;
            }
            for (Pedido p : new ArrayList<>(r.getSecuencia())) {
                solucion.desasignar(p);
                removidos.add(p);
            }
        }
        return removidos;
    }

    private static Set<String> disponibles(ContextoPlanificacion ctx) {
        Set<String> disponibles = new HashSet<>();
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            disponibles.add(v.getCodigo());
        }
        return disponibles;
    }

    /** Unidad con mantenimiento el día de T o que dejó de estar disponible (avería). */
    private static boolean averiado(Ruta r, Set<String> disponibles, ContextoPlanificacion ctx) {
        String codigo = r.getVehiculo().getCodigo();
        return ctx.enMantenimiento(codigo, Turnos.dia(ctx.getMinutoActual()))
                || !disponibles.contains(codigo);
    }

    @Override
    public String nombre() {
        return "vehicle-failure-removal";
    }
}
