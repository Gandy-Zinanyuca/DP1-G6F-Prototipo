package pe.pucp.paqrap.alns;

import java.util.Random;

/**
 * Criterio de aceptación por recocido simulado (<i>simulated annealing</i>).
 *
 * <p>Una solución candidata s′ obtenida tras destruir y reparar se acepta siempre si mejora a
 * la actual, y con probabilidad exp(−[f(s′) − f(s)]/T) si la empeora. La temperatura T decrece
 * geométricamente en cada iteración, de modo que la búsqueda empieza tolerante —aceptando
 * empeoramientos que le permiten cruzar valles— y termina prácticamente golosa.</p>
 *
 * <h2>Calibración de la temperatura inicial</h2>
 * <p>Fijar T₀ como un número absoluto no es transferible entre instancias, porque la escala de
 * f depende del número de pedidos. Se usa la calibración de Ropke y Pisinger: T₀ se elige de
 * modo que una solución un w % peor que la inicial se acepte con probabilidad 1/2, es decir</p>
 * <pre>
 *   T₀ = −(w · f(s₀)) / ln(0,5)
 * </pre>
 * <p>lo que hace el parámetro independiente de la escala del problema y, por lo tanto, válido
 * sin retoques para los tres escenarios de evaluación.</p>
 *
 * <p>El enfriamiento se calcula a partir del presupuesto de iteraciones para que la temperatura
 * final sea una fracción prefijada de la inicial; así el ALNS conserva su carácter
 * <i>anytime</i>: recortar el presupuesto no lo deja atrapado en una fase caliente.</p>
 */
public class CriterioAceptacion {

    private final double factorEnfriamiento;
    private double temperatura;
    private final double temperaturaInicial;

    /**
     * @param costoInicial          valor de la función objetivo de la solución de partida
     * @param porcentajeAceptacion  w: empeoramiento relativo aceptado al 50 % al inicio
     * @param temperaturaFinalRel   fracción de T₀ que debe alcanzarse al agotar el presupuesto
     * @param iteraciones           presupuesto de iteraciones del ALNS
     */
    public CriterioAceptacion(double costoInicial, double porcentajeAceptacion,
                              double temperaturaFinalRel, int iteraciones) {
        double base = Math.max(1.0, Math.abs(costoInicial));
        this.temperaturaInicial = -(porcentajeAceptacion * base) / Math.log(0.5);
        this.temperatura = temperaturaInicial;
        this.factorEnfriamiento = Math.pow(temperaturaFinalRel, 1.0 / Math.max(1, iteraciones));
    }

    /** Decide si la solución candidata reemplaza a la actual. */
    public boolean aceptar(double costoCandidata, double costoActual, Random aleatorio) {
        if (costoCandidata < costoActual) {
            return true;
        }
        double delta = costoCandidata - costoActual;
        double probabilidad = Math.exp(-delta / Math.max(1e-9, temperatura));
        return aleatorio.nextDouble() < probabilidad;
    }

    /** Aplica un paso de enfriamiento; se invoca una vez por iteración del ALNS. */
    public void enfriar() {
        temperatura *= factorEnfriamiento;
    }

    /** Reinicia la temperatura, usado por el mecanismo de reinicio ante estancamiento. */
    public void recalentar(double fraccion) {
        temperatura = Math.max(temperatura, temperaturaInicial * fraccion);
    }

    public double getTemperatura() {
        return temperatura;
    }
}
