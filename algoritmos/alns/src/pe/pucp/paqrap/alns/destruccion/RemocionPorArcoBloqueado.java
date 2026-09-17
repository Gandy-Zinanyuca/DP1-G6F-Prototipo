package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * <b>Operador propio del dominio</b>: blocked-arc removal (ISA 5.2).
 *
 * <pre>
 * arcosBloqueados ← tramos bloqueados vigentes para el instante T
 * PARA CADA ruta de parcial
 *     SI el camino calculado de la ruta atraviesa algún arco de arcosBloqueados
 *         retirar cada pedido de esa ruta cuyo trayecto use un arco bloqueado
 * </pre>
 *
 * <p>El camino de cada tramo se obtiene con la misma función CAMINO_MÁS_RÁPIDO usada en la
 * evaluación de factibilidad, de modo que un arco se considera afectado bajo el mismo criterio.
 * El operador no usa el grado de destrucción: retira todos los pedidos afectados.</p>
 */
public class RemocionPorArcoBloqueado implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        List<Pedido> removidos = new ArrayList<>();
        Set<Long> arcosBloqueados = ctx.getMapa().arcosBloqueadosEn(ctx.getMinutoActual());
        if (arcosBloqueados.isEmpty()) {
            return removidos;
        }
        for (Ruta r : new ArrayList<>(solucion.getRutas())) {
            for (Pedido p : r.pedidosQueAtraviesan(arcosBloqueados, ctx)) {
                solucion.desasignar(p);
                removidos.add(p);
            }
        }
        return removidos;
    }

    @Override
    public String nombre() {
        return "blocked-arc-removal";
    }
}
