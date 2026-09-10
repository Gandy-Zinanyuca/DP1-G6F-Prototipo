package pe.pucp.paqrap.alns;

/**
 * Parámetros de configuración del ALNS.
 *
 * <p>Los valores por defecto siguen los rangos reportados por Ropke y Pisinger (2006) y
 * Pisinger y Ropke (2007) y son el punto de partida para la calibración por experimentación
 * numérica. Todos son configurables sin recompilar, lo que permite ejecutar los tres escenarios
 * del curso con la misma implementación cambiando únicamente esta configuración.</p>
 */
public class ParametrosALNS {

    // ---------------------------------------------------------------- presupuesto

    /** Número máximo de iteraciones del bucle principal. */
    public int maxIteraciones = 3_000;

    /** Presupuesto de tiempo por ciclo de planificación, en milisegundos. */
    public long presupuestoMs = 2_000;

    /** Iteraciones sin mejora tras las cuales se recalienta la temperatura. */
    public int iteracionesParaRecalentar = 400;

    /** Semilla del generador aleatorio; fijarla garantiza ejecuciones reproducibles (LE008). */
    public long semilla = 20262L;

    // ---------------------------------------------------------------- destrucción

    /** Fracción mínima de pedidos removidos por iteración. */
    public double gradoDestruccionMin = 0.10;

    /** Fracción máxima de pedidos removidos por iteración. */
    public double gradoDestruccionMax = 0.35;

    /** Cota absoluta inferior del grado de destrucción, en número de pedidos. */
    public int destruccionMinimaAbsoluta = 2;

    /** Cota absoluta superior del grado de destrucción, en número de pedidos. */
    public int destruccionMaximaAbsoluta = 60;

    /**
     * Si es verdadero, el grado de destrucción se reduce conforme sube la ocupación de la flota.
     *
     * <p>Es la mitigación explícita del riesgo identificado en el informe de selección de
     * algoritmos: cerca del punto de colapso el espacio factible es mínimo, y remover un
     * porcentaje elevado de una solución apenas factible puede producir un estado que los
     * operadores de reparación no logren reconstruir dentro de plazo. Al ligar el grado de
     * destrucción a la ocupación, el algoritmo se vuelve conservador justo cuando la holgura
     * desaparece.</p>
     */
    public boolean destruccionAdaptativaPorOcupacion = true;

    /** Factor de sesgo D de la aleatorización del ranking en la remoción del peor. */
    public double sesgoRemocionPeor = 3.0;

    /** Factor de sesgo D de la aleatorización del ranking en la remoción por relación. */
    public double sesgoRemocionShaw = 5.0;

    /** Pesos φ, χ, ψ de la medida de relación de Shaw (distancia, tiempo, carga). */
    public double shawPesoDistancia = 0.6;
    public double shawPesoTiempo = 0.3;
    public double shawPesoCarga = 0.1;

    // ---------------------------------------------------------------- reparación

    /** Intensidad del ruido aplicado a los costos de inserción; 0 desactiva el ruido. */
    public double factorRuido = 0.025;

    /** Órdenes de arrepentimiento incluidos en la cartera de reparadores. */
    public int[] ordenesArrepentimiento = {2, 3};

    /**
     * Número máximo de pedidos que se intentan reinsertar en una misma reparación.
     *
     * <p>El costo de una reparación crece linealmente con este valor. Acotarlo preserva el
     * número de iteraciones —que es de donde viene la calidad del ALNS— cuando la demanda
     * acumulada supera con mucho la capacidad de la flota. Los pedidos que quedan fuera se
     * eligen por menor criticidad, de modo que el esfuerzo se concentra donde todavía puede
     * evitarse un incumplimiento.</p>
     */
    public int maxPedidosPorReparacion = 120;

    // ---------------------------------------------------------------- adaptación

    /** Longitud del segmento tras el cual se actualizan los pesos de los operadores. */
    public int longitudSegmento = 100;

    /** Factor de reacción λ del suavizado exponencial de pesos. */
    public double factorReaccion = 0.80;

    /** σ₁: puntaje cuando la candidata mejora la mejor solución conocida. */
    public double puntajeNuevoMejor = 33.0;

    /** σ₂: puntaje cuando la candidata mejora la solución actual pero no la mejor. */
    public double puntajeMejora = 13.0;

    /** σ₃: puntaje cuando la candidata empeora pero es aceptada por el criterio. */
    public double puntajeAceptada = 6.0;

    /** Puntaje cuando la candidata es rechazada. */
    public double puntajeRechazada = 0.0;

    // ---------------------------------------------------------------- aceptación

    /** w: empeoramiento relativo aceptado con probabilidad 1/2 al inicio de la búsqueda. */
    public double porcentajeAceptacionInicial = 0.05;

    /** Fracción de T₀ que debe alcanzar la temperatura al agotar el presupuesto. */
    public double temperaturaFinalRelativa = 0.001;

    // ---------------------------------------------------------------- diagnóstico

    /** Emite por consola la traza de convergencia del algoritmo. */
    public boolean traza = false;

    public ParametrosALNS copia() {
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = maxIteraciones;
        p.presupuestoMs = presupuestoMs;
        p.iteracionesParaRecalentar = iteracionesParaRecalentar;
        p.semilla = semilla;
        p.gradoDestruccionMin = gradoDestruccionMin;
        p.gradoDestruccionMax = gradoDestruccionMax;
        p.destruccionMinimaAbsoluta = destruccionMinimaAbsoluta;
        p.destruccionMaximaAbsoluta = destruccionMaximaAbsoluta;
        p.destruccionAdaptativaPorOcupacion = destruccionAdaptativaPorOcupacion;
        p.sesgoRemocionPeor = sesgoRemocionPeor;
        p.sesgoRemocionShaw = sesgoRemocionShaw;
        p.shawPesoDistancia = shawPesoDistancia;
        p.shawPesoTiempo = shawPesoTiempo;
        p.shawPesoCarga = shawPesoCarga;
        p.factorRuido = factorRuido;
        p.ordenesArrepentimiento = ordenesArrepentimiento.clone();
        p.maxPedidosPorReparacion = maxPedidosPorReparacion;
        p.longitudSegmento = longitudSegmento;
        p.factorReaccion = factorReaccion;
        p.puntajeNuevoMejor = puntajeNuevoMejor;
        p.puntajeMejora = puntajeMejora;
        p.puntajeAceptada = puntajeAceptada;
        p.puntajeRechazada = puntajeRechazada;
        p.porcentajeAceptacionInicial = porcentajeAceptacionInicial;
        p.temperaturaFinalRelativa = temperaturaFinalRelativa;
        p.traza = traza;
        return p;
    }
}
