package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Eliminación por peor costo (ISA 5.2).
 *
 * <pre>
 * REPETIR grado veces
 *     PARA CADA pedido aún no removido de parcial
 *         costoInserción[pedido] ← costo(parcial) − costo(parcial sin pedido)
 *     pedido ← pedido con mayor costoInserción
 *     retirar pedido de su ruta en parcial
 * </pre>
 *
 * <p>
 * Retirar un pedido solo altera el costo de su ruta, así que costo(parcial) −
 * costo(parcial sin pedido) = costo(ruta) − costo(ruta sin pedido). Tras cada
 * retiro solo se recalculan los valores de los pedidos de la ruta modificada;
 * los demás no cambian.
 * </p>
 */
public class RemocionPeor implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        Map<Pedido, Double> costoInsercion = new LinkedHashMap<>();
        for (Ruta r : solucion.getRutas()) {
            calcularRuta(r, costoInsercion, ctx);
        }

        List<Pedido> removidos = new ArrayList<>();
        while (removidos.size() < q && !costoInsercion.isEmpty()) {
            Pedido peor = null;
            double mayor = Double.NEGATIVE_INFINITY;
            for (Map.Entry<Pedido, Double> e : costoInsercion.entrySet()) {
                if (e.getValue() > mayor) {
                    mayor = e.getValue();
                    peor = e.getKey();
                }
            }
            Ruta r = solucion.desasignar(peor);
            removidos.add(peor);
            costoInsercion.remove(peor);
            if (r != null) {
                calcularRuta(r, costoInsercion, ctx);
            }
        }
        return removidos;
    }

    private static void calcularRuta(Ruta r, Map<Pedido, Double> costoInsercion, ContextoPlanificacion ctx) {
        if (r.estaVacia()) {
            return;
        }
        r.recalcular(ctx);
        double costoRuta = r.costoOperacion();
        for (int i = 0; i < r.tamanio(); i++) {
            Pedido p = r.remover(i);
            r.recalcular(ctx);
            costoInsercion.put(p, costoRuta - r.costoOperacion());
            r.insertar(i, p);
        }
        r.recalcular(ctx);
    }

    @Override
    public String nombre() {
        return "eliminacion-peor-costo";
    }
}
