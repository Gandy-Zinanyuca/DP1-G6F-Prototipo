package pe.pucp.paqrap.planificador;

import pe.pucp.paqrap.solucion.Solucion;

/**
 * Contrato del componente planificador.
 *
 * <p>Existe para que los dos algoritmos exigidos por el requisito no funcional RNF01 —ALNS y
 * Búsqueda Tabú— sean intercambiables por parámetro. El simulador y el visualizador dependen de
 * esta interfaz y no de una implementación concreta, de modo que la experimentación numérica
 * consiste en ejecutar el mismo escenario cambiando únicamente la instancia inyectada aquí.</p>
 *
 * <p>Esa intercambiabilidad es, además, la diferencia de producto que el equipo identificó
 * frente a las alternativas comerciales evaluadas: ninguna permite sustituir la metaheurística
 * del motor de ruteo.</p>
 */
public interface Planificador {

    /**
     * Produce la asignación de rutas del ciclo.
     *
     * @param ctx        fotografía de la operación al inicio del ciclo
     * @param planPrevio plan del ciclo anterior, o {@code null} para planificar desde cero.
     *                   Pasarlo convierte la llamada en una reoptimización incremental.
     * @return la solución con una ruta por unidad utilizada y los pedidos diferidos
     */
    Solucion planificar(ContextoPlanificacion ctx, Solucion planPrevio);

    /** Nombre del algoritmo, usado en los reportes de experimentación numérica. */
    String nombre();

    /** Resumen legible de la última ejecución: iteraciones, tiempo y convergencia. */
    String resumenUltimaEjecucion();
}
