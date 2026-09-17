package pe.pucp.paqrap.servicios;

import pe.pucp.paqrap.servicios.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Construcción de la solución inicial del ciclo.
 *
 * <p>El ALNS no necesita una solución de partida buena —su trabajo es mejorarla—, pero sí una
 * <b>factible y obtenida rápido</b>, porque cada ciclo de planificación dispone de segundos.
 * Se usa una heurística de inserción golosa secuencial que coloca los pedidos en orden de
 * criticidad decreciente: primero los que tienen menos minutos hasta su hora límite.</p>
 *
 * <p>Ese orden no es una preferencia estética. Como el incumplimiento de un solo plazo termina
 * el escenario, la solución inicial debe garantizar sitio a los pedidos urgentes antes de
 * ocupar la flota con los que aún tienen 30 horas de margen. Un pedido regular desplazado a un
 * ciclo posterior no cuesta nada; un pedido priorizado desplazado puede costar el escenario.</p>
 *
 * <p>La construcción admite dos pasadas: la primera exige inserciones sin tardanza; la segunda
 * recoge los que quedaron fuera y los coloca aunque generen tardanza, de modo que la función
 * objetivo pueda "verlos" y la búsqueda tenga desde dónde repararlos. Los que ni así encuentran
 * sitio quedan sin asignar y serán penalizados por criticidad.</p>
 */
public final class ConstructorInicial {

    private ConstructorInicial() {
    }

    public static Solucion construir(ContextoPlanificacion ctx) {
        Solucion s = new Solucion();
        final int ahora = ctx.getMinutoActual();

        List<Pedido> ordenados = new ArrayList<>(ctx.getPedidosPorAtender());
        ordenados.sort(Comparator
                .comparingInt((Pedido p) -> p.holgura(ahora))
                .thenComparingInt(Pedido::getId));       // desempate estable ⇒ reproducible

        // Primera pasada: solo inserciones admisibles (sin tardanza ni violación dura).
        for (Pedido p : ordenados) {
            Insercion ins = EvaluadorInsercion.mejorInsercion(s, p, ctx, true);
            if (ins == null) {
                s.marcarNoAsignado(p);
            } else {
                EvaluadorInsercion.aplicar(s, ins, p, ctx);
            }
        }

        // Segunda pasada: se admite tardanza con tal de que el pedido entre en la solución.
        EvaluadorInsercion.colocarRezagados(s, ctx);

        s.evaluar(ctx);
        return s;
    }
}
