package pe.pucp.paqrap.alns.reparacion;

import pe.pucp.paqrap.alns.EvaluadorInsercion;
import pe.pucp.paqrap.alns.OperadorReparacion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Inserción voraz (ISA 5.2).
 *
 * <pre>
 * PARA CADA pedido en removidos, en orden de deadline ascendente
 *     mejorInserción ← inserción factible de menor costo (todo vehículo, toda posición)
 *     SI no existe, u obliga a otra recarga → probar repartir el pedido entre varias unidades
 *     aplicar lo más barato ; SI nada es posible → marcar pedido como no asignado
 * </pre>
 */
public class InsercionGolosa implements OperadorReparacion {

    @Override
    public void reparar(Solucion solucion, List<Pedido> removidos,
                        ContextoPlanificacion ctx, Random aleatorio) {
        List<Pedido> ordenados = new ArrayList<>(removidos);
        ordenados.sort(Comparator.comparingInt(Pedido::getMinutoLimite).thenComparingInt(Pedido::getId));

        for (Pedido p : ordenados) {
            if (EvaluadorInsercion.insertar(solucion, p, ctx) == EvaluadorInsercion.Resultado.NINGUNA) {
                solucion.marcarNoAsignado(p);
            }
        }
    }

    @Override
    public String nombre() {
        return "insercion-voraz";
    }
}
