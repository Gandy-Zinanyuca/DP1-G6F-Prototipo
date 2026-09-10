package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Remoción de ruta completa (<i>route removal</i>).
 *
 * <p>Vacía por completo una o más rutas elegidas al azar hasta alcanzar el grado de
 * destrucción. Es el único operador capaz de <b>reducir el número de unidades en servicio</b>:
 * los operadores que remueven pedidos sueltos casi nunca dejan una ruta vacía, de modo que la
 * flota utilizada solo baja si alguna ruta se desmantela entera y sus pedidos encuentran sitio
 * en las demás.</p>
 *
 * <p>En el escenario de colapso este operador tiene un segundo papel: liberar de golpe una
 * unidad completa da al reparador la libertad de reconstruir una ruta desde cero alrededor de
 * los pedidos más críticos, en vez de encajarlos en los huecos de rutas ya comprometidas.</p>
 */
public class RemocionDeRuta implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        List<Ruta> conPedidos = new ArrayList<>();
        for (Ruta r : solucion.getRutas()) {
            if (!r.estaVacia()) {
                conPedidos.add(r);
            }
        }
        Collections.shuffle(conPedidos, aleatorio);

        List<Pedido> removidos = new ArrayList<>();
        for (Ruta r : conPedidos) {
            if (removidos.size() >= q) {
                break;
            }
            for (Pedido p : new ArrayList<>(r.getSecuencia())) {
                solucion.desasignar(p);
                removidos.add(p);
            }
            r.recalcular(ctx);
        }
        return removidos;
    }

    @Override
    public String nombre() {
        return "remocion-de-ruta";
    }
}
