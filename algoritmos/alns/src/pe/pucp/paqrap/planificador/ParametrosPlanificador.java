package pe.pucp.paqrap.planificador;

/**
 * Parámetros del modelo del problema, comunes a los dos algoritmos del componente planificador.
 *
 * <p>Conforme al ISA (secciones 5.1 y 5.2), las restricciones son <b>duras</b>: una solución
 * con cualquier entrega fuera de plazo, pedido sin asignar o violación de capacidad,
 * mantenimiento, turno o inventario no es factible, y el costo —distancia × costo por km— solo
 * se usa para comparar soluciones factibles. Por eso aquí no hay penalizaciones.</p>
 */
public class ParametrosPlanificador {

    /**
     * Confinamiento de la ruta al turno en que inicia (LE017).
     *
     * <p>Con la lectura estricta (verdadero) la ruta completa debe terminar antes del cambio de
     * turno; con la operativa (falso, por defecto) la asignación se hace dentro del bloque y la
     * unidad puede ser relevada por el turno entrante.</p>
     */
    public boolean limitarRutaAlTurno = false;

    /** Tiempo de entrega al destinatario, en minutos (enunciado: 1 hora). */
    public int minutosEntrega = 60;

    /** Umbrales del semáforo de inventario, como fracción de la capacidad (LE028). */
    public double umbralSemaforoAmbar = 0.40;
    public double umbralSemaforoRojo = 0.15;

    public ParametrosPlanificador copia() {
        ParametrosPlanificador p = new ParametrosPlanificador();
        p.limitarRutaAlTurno = limitarRutaAlTurno;
        p.minutosEntrega = minutosEntrega;
        p.umbralSemaforoAmbar = umbralSemaforoAmbar;
        p.umbralSemaforoRojo = umbralSemaforoRojo;
        return p;
    }
}
