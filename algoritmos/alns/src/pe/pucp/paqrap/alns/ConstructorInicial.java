package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GENERAR_SOLUCIÓN_INICIAL del ISA (sección 5.1), reutilizada sin modificaciones por ALNS.
 *
 * <p>Construcción determinista —no es otra metaheurística—: los pedidos se ordenan por
 * deadline ascendente y cada uno se inserta de la forma más barata ({@link
 * EvaluadorInsercion#insertar}): completo en la posición factible de menor costo, o repartido
 * entre varias unidades si eso cuesta menos o si ninguna lo admite completo.
 * Si tampoco así admite inserción factible, se marca como no asignado: si el pedido todavía
 * tiene holgura para atenderse en un ciclo posterior ({@link ContextoPlanificacion#esPostergable})
 * queda reprogramado y la construcción sigue; si no, la solución inicial es no factible.</p>
 */
public final class ConstructorInicial {

    private ConstructorInicial() {
    }

    public static Solucion construir(ContextoPlanificacion ctx) {
        Solucion s = new Solucion();

        List<Pedido> ordenados = new ArrayList<>(ctx.getPedidosPorAtender());
        ordenados.sort(Comparator
                .comparingInt(Pedido::getMinutoLimite)
                .thenComparingInt(Pedido::getId));       // desempate estable ⇒ reproducible

        for (Pedido p : ordenados) {
            if (EvaluadorInsercion.insertar(s, p, ctx) == EvaluadorInsercion.Resultado.NINGUNA) {
                s.marcarNoAsignado(p);
                if (ctx.esPostergable(p)) {
                    continue;   // reprogramado: se atenderá en un ciclo posterior
                }
                break;   // RETORNAR solución inicial no factible
            }
        }

        s.evaluar(ctx);
        return s;
    }
}
