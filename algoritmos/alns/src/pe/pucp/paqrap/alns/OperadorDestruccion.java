package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.List;
import java.util.Random;

/**
 * Operador de destrucción: retira {@code q} pedidos de la solución y los deja sin asignar.
 *
 * <p>El contrato es estricto: el operador <b>solo</b> desasigna pedidos mediante
 * {@link Solucion#desasignar}; nunca reordena rutas ni intenta reparar nada. Toda la
 * reconstrucción es responsabilidad del operador de reparación. Esa separación es lo que
 * permite combinar libremente cualquier destructor con cualquier reparador y es la razón por
 * la que el mecanismo adaptativo puede aprender qué pares funcionan mejor.</p>
 */
public interface OperadorDestruccion {

    /**
     * Retira pedidos de la solución.
     *
     * @param q número objetivo de pedidos a remover (grado de destrucción)
     * @return los pedidos efectivamente removidos
     */
    List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio);

    /**
     * Indica si el operador puede remover algo en esta ejecución, a partir de la solución inicial.
     * ALNS no sortea los operadores no aplicables, para no gastar iteraciones en destrucciones
     * vacías. Por defecto, verdadero; los operadores de dominio dependen de las incidencias
     * vigentes (averías, mantenimientos, bloqueos).
     */
    default boolean aplicable(Solucion solucion, ContextoPlanificacion ctx) {
        return true;
    }

    /** Nombre del operador, usado en las estadísticas de pesos adaptativos. */
    String nombre();
}
