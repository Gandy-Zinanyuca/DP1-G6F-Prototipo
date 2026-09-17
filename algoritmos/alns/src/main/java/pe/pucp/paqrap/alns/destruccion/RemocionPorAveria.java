package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * <b>Operador propio del dominio</b>: remoción por avería de unidad
 * (<i>vehicle-failure removal</i>).
 *
 * <p>Retira todos los pedidos asignados a unidades que dejaron de estar disponibles —por avería
 * de tipo 1, 2 o 3, o por mantenimiento preventivo— y, si hace falta completar el grado de
 * destrucción, sigue con los pedidos más críticos de la solución.</p>
 *
 * <p>Es el operador que materializa la ventaja que el informe de selección atribuye al ALNS
 * frente a la búsqueda tabú: la avería de una unidad cargada se resuelve en <b>un solo ciclo</b>
 * de destrucción y reparación, mientras que un método de vecindario pequeño necesita encadenar
 * muchos movimientos individuales para reconfigurar el plan.</p>
 *
 * <p>El relleno por criticidad no es arbitrario: LE096 obliga a atender primero los pedidos con
 * menor tiempo restante hasta su plazo comprometido, y liberarlos es el paso previo para que el
 * reparador pueda colocarlos en la unidad que llegue antes.</p>
 */
public class RemocionPorAveria implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        Set<String> asignables = new HashSet<>();
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            asignables.add(v.getCodigo());
        }

        List<Pedido> comprometidos = new ArrayList<>();
        for (Ruta r : solucion.getRutas()) {
            if (r.estaVacia() || asignables.contains(r.getVehiculo().getCodigo())) {
                continue;
            }
            comprometidos.addAll(r.getSecuencia());
        }

        List<Pedido> removidos = new ArrayList<>();
        for (Pedido p : new ArrayList<>(comprometidos)) {
            solucion.desasignar(p);
            removidos.add(p);
        }

        // Completar con los pedidos más críticos (LE096, LE097).
        if (removidos.size() < q) {
            List<Pedido> resto = new ArrayList<>(solucion.pedidosAsignados());
            final int ahora = ctx.getMinutoActual();
            resto.sort(Comparator.comparingInt(p -> p.holgura(ahora)));

            // Sesgo suave hacia los más críticos sin volver el operador determinista.
            int ventana = Math.min(resto.size(), Math.max(q * 2, 10));
            List<Pedido> candidatos = new ArrayList<>(resto.subList(0, ventana));
            Collections.shuffle(candidatos, aleatorio);

            for (Pedido p : candidatos) {
                if (removidos.size() >= q) {
                    break;
                }
                solucion.desasignar(p);
                removidos.add(p);
            }
        }
        return removidos;
    }

    @Override
    public String nombre() {
        return "remocion-averia";
    }
}
