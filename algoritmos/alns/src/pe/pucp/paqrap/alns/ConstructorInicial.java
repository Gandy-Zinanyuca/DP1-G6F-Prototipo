package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.alns.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GENERAR_SOLUCIÓN_INICIAL del ISA (sección 5.1), reutilizada sin
 * modificaciones por ALNS.
 *
 * <p>
 * Construcción determinista —no es otra metaheurística—: los pedidos se ordenan
 * por deadline ascendente y cada uno se inserta en la posición factible de
 * menor costo. Si ninguna unidad admite el pedido completo, se reparte entre
 * varias ({@link EvaluadorInsercion#insertarFraccionado}), igual que en la
 * solución inicial de Búsqueda Tabú. Si tampoco así admite inserción factible,
 * se marca como no asignado: si el pedido todavía tiene holgura para atenderse
 * en un ciclo posterior ({@link ContextoPlanificacion#esPostergable}) queda
 * reprogramado y la construcción sigue; si no, la solución inicial es no
 * factible.
 * </p>
 */
public final class ConstructorInicial {

    private ConstructorInicial() {
    }

    public static Solucion construir(ContextoPlanificacion ctx) {
        Solucion s = new Solucion();

        List<Pedido> ordenados = new ArrayList<>(ctx.getPedidosPorAtender());
        ordenados.sort(Comparator.comparingInt(Pedido::getMinutoLimite).thenComparingInt(Pedido::getId)); // desempate
                                                                                                          // estable ⇒
                                                                                                          // reproducible

        for (Pedido p : ordenados) {
            Insercion mejor = EvaluadorInsercion.mejorInsercion(s, p, ctx);
            if (mejor != null) {
                EvaluadorInsercion.aplicar(s, mejor, p, ctx);
                continue;
            }
            // No cabe completo en ninguna unidad: se intenta repartir antes de rendirse.
            if (!EvaluadorInsercion.insertarFraccionado(s, p, ctx)) {
                s.marcarNoAsignado(p);
                if (ctx.esPostergable(p)) {
                    continue; // reprogramado: se atenderá en un ciclo posterior
                }
                break; // RETORNAR solución inicial no factible
            }
        }

        s.evaluar(ctx);
        return s;
    }
}
