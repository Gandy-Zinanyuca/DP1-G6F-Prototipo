package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Remoción aleatoria (<i>random removal</i>).
 *
 * <p>Elige q pedidos al azar entre los asignados y los retira. Es el operador de
 * diversificación puro: no explota ninguna estructura del problema, y precisamente por eso es
 * el que permite escapar de óptimos locales que los operadores dirigidos refuerzan. En el
 * esquema adaptativo su peso tiende a bajar en fases de intensificación y a recuperarse cuando
 * la búsqueda se estanca.</p>
 *
 * <p>Complejidad: O(n) para recolectar los asignados más O(q) remociones en O(1) cada una
 * gracias al índice inverso de la solución.</p>
 */
public class RemocionAleatoria implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        List<Pedido> asignados = new ArrayList<>(solucion.pedidosAsignados());
        Collections.shuffle(asignados, aleatorio);

        List<Pedido> removidos = new ArrayList<>();
        int limite = Math.min(q, asignados.size());
        for (int i = 0; i < limite; i++) {
            Pedido p = asignados.get(i);
            solucion.desasignar(p);
            removidos.add(p);
        }
        return removidos;
    }

    @Override
    public String nombre() {
        return "remocion-aleatoria";
    }
}
