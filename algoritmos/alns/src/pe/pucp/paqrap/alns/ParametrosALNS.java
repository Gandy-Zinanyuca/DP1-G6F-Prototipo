package pe.pucp.paqrap.alns;

/**
 * ConfiguracionALNS del ISA (sección 5.2).
 *
 * <p>
 * El ISA no fija valores numéricos para estos parámetros: deben determinarse
 * mediante experimentación numérica. Los valores por defecto son solo un punto
 * de partida para la calibración.
 * </p>
 */
public class ParametrosALNS {

    // ---------------------------------------------------------------- ventana de
    // consumo

    /**
     * Sa: salto de planificación, en minutos (cada cuánto se ejecuta el
     * planificador).
     */
    public long saMinutos = 10;

    /** K: factor de consumo, a calibrar. */
    public int k = 7;

    /** Sc = Sa × K: amplitud, en minutos, de la ventana de pedidos considerados. */
    public long scMinutos() {
        return saMinutos * k;
    }

    // ---------------------------------------------------------------- búsqueda

    /** Número máximo de iteraciones del bucle principal. */
    public int maxIteraciones = 1_000;

    /**
     * Proporción de pedidos de la solución que remueve un operador de destrucción.
     */
    public double proporcionDestruccion = 0.20;

    /**
     * Reducción dinámica del grado de destrucción conforme aumenta la ocupación de
     * la flota (ISA 4.1 y 5.2): grado = total × proporción × (1 − 0,7 · ocupación).
     */
    public boolean destruccionAdaptativaPorOcupacion = true;

    /**
     * Semilla del generador aleatorio; fijarla garantiza ejecuciones reproducibles
     * (LE008).
     */
    public long semilla = 20262L;

    // ---------------------------------------------------------------- adaptación

    /** Iteraciones por segmento, al cabo de las cuales se actualizan los pesos. */
    public int tamanioSegmento = 100;

    /** Peso inicial de cada operador. */
    public double pesoInicial = 1.0;

    /** Factor de reacción r: w ← w·(1 − r) + r·desempeñoSegmento. */
    public double factorReaccion = 0.10;

    /** Puntuación cuando el candidato mejora a mejorGlobal. */
    public double puntuacionNuevoMejor = 33.0;

    /**
     * Puntuación cuando el candidato mejora a la solución actual sin ser nuevo
     * mejor.
     */
    public double puntuacionMejoraActual = 9.0;

    /** Puntuación cuando el candidato no mejora a la actual pero es aceptado. */
    public double puntuacionAceptacionNoMejora = 13.0;

    /** Puntuación cuando el candidato no es factible o es rechazado. */
    public double puntuacionRechazo = 0.0;

    // ---------------------------------------------------------------- reparación

    /** Arrepentimiento asignado a un pedido con una sola inserción factible. */
    public double arrepentimientoSinAlternativa = 1_000_000.0;

    // ---------------------------------------------------------------- aceptación

    /** Temperatura inicial del criterio tipo recocido simulado. */
    public double temperaturaInicial = 100.0;

    /**
     * Factor de enfriamiento: T = temperaturaInicial × factorEnfriamiento ^
     * iteración.
     */
    public double factorEnfriamiento = 0.995;

    // ---------------------------------------------------------------- diagnóstico

    /** Emite por consola la traza de convergencia del algoritmo. */
    public boolean traza = false;

    public ParametrosALNS copia() {
        ParametrosALNS p = new ParametrosALNS();
        p.saMinutos = saMinutos;
        p.k = k;
        p.maxIteraciones = maxIteraciones;
        p.proporcionDestruccion = proporcionDestruccion;
        p.destruccionAdaptativaPorOcupacion = destruccionAdaptativaPorOcupacion;
        p.semilla = semilla;
        p.tamanioSegmento = tamanioSegmento;
        p.pesoInicial = pesoInicial;
        p.factorReaccion = factorReaccion;
        p.puntuacionNuevoMejor = puntuacionNuevoMejor;
        p.puntuacionMejoraActual = puntuacionMejoraActual;
        p.puntuacionAceptacionNoMejora = puntuacionAceptacionNoMejora;
        p.puntuacionRechazo = puntuacionRechazo;
        p.arrepentimientoSinAlternativa = arrepentimientoSinAlternativa;
        p.temperaturaInicial = temperaturaInicial;
        p.factorEnfriamiento = factorEnfriamiento;
        p.traza = traza;
        return p;
    }
}
