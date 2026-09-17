package pe.pucp.paqrap.planificador;

/**
 * Parámetros de la función objetivo y del manejo de restricciones, comunes a los dos
 * algoritmos del componente planificador.
 *
 * <p>Separar estos valores del algoritmo permite que ALNS y Búsqueda Tabú se comparen en la
 * experimentación numérica bajo exactamente la misma función objetivo, que es la condición
 * para que el contraste entre ambos sea válido.</p>
 *
 * <h2>Estructura lexicográfica de la función objetivo</h2>
 * <p>Como el incumplimiento de un solo plazo constituye colapso logístico, las penalizaciones
 * están escaladas para inducir un orden lexicográfico de hecho: primero eliminar tardanzas,
 * después colocar todos los pedidos, y solo entonces minimizar el costo de operación. Los
 * factores son parámetros para que la experimentación numérica pueda explorar otros balances.</p>
 */
public class ParametrosPlanificador {

    /**
     * Penalización fija por dejar un pedido sin asignar en el ciclo. Es el término dominante:
     * un pedido entregado con retraso sigue siendo una entrega, mientras que uno no despachado
     * es una entrega que no ocurrirá. La escala garantiza que el algoritmo prefiera siempre
     * despachar tarde antes que no despachar.
     */
    public double penalizacionPedidoNoAsignado = 1_000_000.0;

    /**
     * Factor que multiplica la penalización de un pedido no asignado según su criticidad.
     * Un pedido con el 90 % de su plazo consumido pesa mucho más que uno recién registrado,
     * lo que implementa la priorización por tiempo restante exigida por LE096.
     */
    public double factorCriticidadNoAsignado = 500_000.0;

    /**
     * Penalización fija por cada pedido que llega fuera de su hora límite. Domina sobre
     * cualquier consideración de costo: un solo incumplimiento constituye colapso logístico.
     */
    public double penalizacionPedidoTardio = 200_000.0;

    /**
     * Penalización por minuto de tardanza. Es deliberadamente pequeña frente al término fijo:
     * su papel no es decidir entre soluciones con y sin incumplimientos —eso ya lo resuelve
     * {@link #penalizacionPedidoTardio}— sino discriminar entre dos soluciones igualmente
     * incumplidoras, prefiriendo la que se aleja menos del plazo.
     */
    public double penalizacionPorMinutoTardanza = 10.0;

    /** Costo fijo por poner una unidad en operación; desalienta usar flota innecesaria. */
    public double costoFijoPorUnidad = 50.0;

    /** Peso del costo monetario de distancia (S/ por km según el tipo de unidad). */
    public double pesoCostoDistancia = 1.0;

    /**
     * Confinamiento de la ruta al turno en que inicia.
     *
     * <p>LE017 exige que la asignación de una unidad a una ruta ocurra dentro de uno de los tres
     * bloques de turno. Admite dos lecturas: la estricta, en la que la ruta completa debe
     * terminar antes del cambio de turno; y la operativa, en la que la asignación se hace dentro
     * del bloque pero la unidad puede ser relevada por el turno entrante y continuar el
     * recorrido. Con la lectura estricta activada, una bicicleta a 12 km/h no puede cruzar la
     * ciudad dentro de una jornada de ocho horas menos la hora de alimentación, lo que deja sin
     * cobertura a buena parte del mapa.</p>
     *
     * <p>Por eso el valor por defecto es la lectura operativa. Poner el parámetro en verdadero
     * activa la estricta sin ningún otro cambio en el código, de modo que la experimentación
     * numérica puede reportar ambas.</p>
     */
    public boolean limitarRutaAlTurno = false;

    /** Tiempo de entrega al destinatario, en minutos (enunciado: 1 hora). */
    public int minutosEntrega = 60;

    /** Cadencia del ciclo de planificación en minutos simulados (LE026). */
    public int minutosPorCicloPlanificacion = 15;

    /** Umbrales del semáforo de inventario, como fracción de la capacidad (LE028). */
    public double umbralSemaforoAmbar = 0.40;
    public double umbralSemaforoRojo = 0.15;

    /**
     * Horizonte de atención: en un ciclo solo se consideran los pedidos cuya hora límite cae
     * dentro de esta ventana. Evita que el planificador consuma flota hoy para pedidos
     * regulares de 36 h que pueden esperar, reservando capacidad para los priorizados que
     * lleguen en las próximas horas. Es un parámetro sensible: acortarlo protege a los pedidos
     * urgentes pero desaprovecha flota ociosa; alargarlo hace lo contrario.
     */
    public int horizonteAtencionMinutos = 24 * 60;

    /**
     * Número máximo de pedidos que un ciclo entrega al algoritmo, ordenados por criticidad.
     *
     * <p>Es una salvaguarda de escalabilidad, no un recorte de alcance. La capacidad instalada
     * de la flota por ciclo está acotada por la suma de capacidades de las unidades; considerar
     * un múltiplo holgado de esa cifra basta para que la solución no pierda calidad, mientras
     * que considerar miles de pedidos que ninguna unidad podrá tomar multiplica el costo de cada
     * evaluación de inserción sin mejorar el plan. Los pedidos excluidos no se pierden: vuelven
     * a competir en el ciclo siguiente, y su criticidad habrá aumentado.</p>
     */
    public int maxPedidosPorCiclo = 400;

    /**
     * Anticipación máxima con que se asigna trabajo a una unidad todavía ocupada.
     *
     * <p>Una unidad que regresa dentro de veinte minutos es un recurso real para el ciclo
     * actual; una que regresa dentro de nueve horas no lo es, y asignarle pedidos solo produce
     * rutas que empiezan tardísimo y llegan fuera de plazo. Limitar la anticipación mantiene el
     * plan del ciclo pegado a la capacidad efectivamente disponible y deja que los pedidos
     * restantes se replanifiquen cuando haya unidades libres de verdad.</p>
     */
    public int ventanaDisponibilidadUnidadMinutos = 4 * 60;

    public ParametrosPlanificador copia() {
        ParametrosPlanificador p = new ParametrosPlanificador();
        p.penalizacionPorMinutoTardanza = penalizacionPorMinutoTardanza;
        p.penalizacionPedidoTardio = penalizacionPedidoTardio;
        p.penalizacionPedidoNoAsignado = penalizacionPedidoNoAsignado;
        p.factorCriticidadNoAsignado = factorCriticidadNoAsignado;
        p.costoFijoPorUnidad = costoFijoPorUnidad;
        p.pesoCostoDistancia = pesoCostoDistancia;
        p.limitarRutaAlTurno = limitarRutaAlTurno;
        p.minutosEntrega = minutosEntrega;
        p.minutosPorCicloPlanificacion = minutosPorCicloPlanificacion;
        p.umbralSemaforoAmbar = umbralSemaforoAmbar;
        p.umbralSemaforoRojo = umbralSemaforoRojo;
        p.horizonteAtencionMinutos = horizonteAtencionMinutos;
        p.maxPedidosPorCiclo = maxPedidosPorCiclo;
        p.ventanaDisponibilidadUnidadMinutos = ventanaDisponibilidadUnidadMinutos;
        return p;
    }
}
