package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.List;
import java.util.Random;

/**
 * Operador de reparación: reinserta en la solución los pedidos que quedaron sin asignar.
 *
 * <p>Un reparador puede dejar pedidos fuera si ninguna inserción respeta las restricciones;
 * esos pedidos permanecen en {@link Solucion#getNoAsignados()} y la función objetivo los
 * penaliza en proporción a su criticidad, de modo que la búsqueda vuelve sobre ellos en las
 * iteraciones siguientes.</p>
 */
public interface OperadorReparacion {

    /**
     * Reinserta los pedidos indicados. La implementación decide el orden de inserción, que es
     * precisamente lo que distingue a un reparador goloso de uno por arrepentimiento.
     */
    void reparar(Solucion solucion, List<Pedido> porInsertar,
                 ContextoPlanificacion ctx, Random aleatorio);

    String nombre();
}
