package pe.pucp.paqrap.planificador;

/**
 * Parámetros del modelo del problema, comunes a los dos algoritmos del componente planificador.
 *
 * <p>Conforme al ISA (secciones 5.1 y 5.2), las restricciones son <b>duras</b>: una solución
 * con cualquier entrega fuera de plazo, pedido sin asignar o violación de capacidad,
 * mantenimiento, turno o inventario no es factible, y el costo —distancia × costo por km— solo
 * se usa para comparar soluciones factibles.</p>
 *
 * <p>Excepción: un pedido con holgura suficiente puede <b>reprogramarse</b> (quedar sin asignar
 * en este plan y atenderse en un ciclo posterior) para cumplir con pedidos más urgentes. No hace
 * infactible la solución, pero cada paquete reprogramado suma una penalización al costo, de modo
 * que la búsqueda solo reprograma cuando no puede atenderlo ahora.</p>
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

    /**
     * Viajes múltiples: la unidad puede volver a cualquier almacén con stock, recargar y seguir
     * repartiendo dentro de la misma ruta (enunciado del curso, pregunta 10). Falso: un viaje.
     */
    public boolean permitirRecargas = true;

    /**
     * Máximo de viajes que una ruta puede encadenar en un plan. Acota el horizonte de cada ruta
     * —el plan se rehace cada Sa minutos— y el costo de la búsqueda; lo que no cabe y tiene
     * holgura se reprograma. Por defecto 3, el mínimo de viajes diarios de un auto en el enunciado.
     */
    public int maxViajesPorRuta = 3;

    /** Permite reprogramar pedidos con holgura a un ciclo posterior (ver la descripción). */
    public boolean permitirPostergacion = true;

    /**
     * Holgura mínima, en minutos, que debe tener un pedido (hora límite − T) para poder
     * reprogramarse. Un pedido con menos holgura debe entrar en el plan; si no cabe, colapso.
     * Con 240 min, los pedidos de plazo 4 h nunca se reprograman una vez registrados.
     */
    public int holguraMinimaPostergacionMin = 240;

    /** Penalización por paquete reprogramado: domina cualquier diferencia de costo de rutas. */
    public double penalizacionPorPaquetePostergado = 1_000_000.0;

    /** Tiempo de entrega al destinatario, en minutos (enunciado: 1 hora). */
    public int minutosEntrega = 60;

    /** Umbrales del semáforo de inventario, como fracción de la capacidad (LE028). */
    public double umbralSemaforoAmbar = 0.40;
    public double umbralSemaforoRojo = 0.15;

    public ParametrosPlanificador copia() {
        ParametrosPlanificador p = new ParametrosPlanificador();
        p.limitarRutaAlTurno = limitarRutaAlTurno;
        p.permitirRecargas = permitirRecargas;
        p.maxViajesPorRuta = maxViajesPorRuta;
        p.permitirPostergacion = permitirPostergacion;
        p.holguraMinimaPostergacionMin = holguraMinimaPostergacionMin;
        p.penalizacionPorPaquetePostergado = penalizacionPorPaquetePostergado;
        p.minutosEntrega = minutosEntrega;
        p.umbralSemaforoAmbar = umbralSemaforoAmbar;
        p.umbralSemaforoRojo = umbralSemaforoRojo;
        return p;
    }
}
