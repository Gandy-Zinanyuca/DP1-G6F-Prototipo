package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.List;
import java.util.Random;

/**
 * Operador de reparación: reinserta en la solución los pedidos removidos por la destrucción.
 *
 * <p>Solo se aplican inserciones factibles. Un pedido sin inserción factible queda marcado como
 * no asignado, lo que vuelve no factible al candidato (restricción dura del ISA).</p>
 */
public interface OperadorReparacion {

    void reparar(Solucion solucion, List<Pedido> removidos,
                 ContextoPlanificacion ctx, Random aleatorio);

    String nombre();
}
