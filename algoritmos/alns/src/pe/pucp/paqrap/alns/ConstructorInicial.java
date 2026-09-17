package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.alns.EvaluadorInsercion.Insercion;
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
 * deadline ascendente y cada uno se inserta en la posición factible de menor costo. Si algún
 * pedido no admite inserción factible, se marca como no asignado y la solución inicial es no
 * factible.</p>
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
            Insercion mejor = EvaluadorInsercion.mejorInsercion(s, p, ctx);
            if (mejor == null) {
                s.marcarNoAsignado(p);
                break;   // RETORNAR solución inicial no factible
            }
            EvaluadorInsercion.aplicar(s, mejor, p, ctx);
        }

        s.evaluar(ctx);
        return s;
    }
}
