package pe.pucp.paqrap.alns;

import java.util.Random;

/**
 * CRITERIO_ACEPTACIÓN y CALCULAR_TEMPERATURA del ISA (sección 5.2): esquema
 * tipo recocido simulado.
 *
 * <pre>
 * SI costo(candidato) &lt; costo(actual) → aceptar con puntuaciónMejoraActual
 * temperatura ← temperaturaInicial × factorEnfriamiento ^ iteración
 * SI U(0,1) &lt; exp(−(costo(candidato) − costo(actual)) / temperatura)
 *     → aceptar con puntuaciónAceptaciónNoMejora
 * SINO rechazar con puntuaciónRechazo
 * </pre>
 */
public class CriterioAceptacion {

    /**
     * Resultado de la aceptación: si se acepta y la puntuación que recibe el par de
     * operadores.
     */
    public static final class Resultado {
        public final boolean aceptar;
        public final double puntuacion;

        Resultado(boolean aceptar, double puntuacion) {
            this.aceptar = aceptar;
            this.puntuacion = puntuacion;
        }
    }

    private final ParametrosALNS par;

    public CriterioAceptacion(ParametrosALNS par) {
        this.par = par;
    }

    public Resultado evaluar(double costoActual, double costoCandidato, int iteracion, Random aleatorio) {
        if (costoCandidato < costoActual) {
            return new Resultado(true, par.puntuacionMejoraActual);
        }
        // Evita dividir entre cero cuando la potencia se desborda por debajo.
        double temperatura = Math.max(calcularTemperatura(iteracion), Double.MIN_VALUE);
        double probabilidadAceptacion = Math.exp(-(costoCandidato - costoActual) / temperatura);
        double valorAleatorio = aleatorio.nextDouble();
        if (valorAleatorio < probabilidadAceptacion) {
            return new Resultado(true, par.puntuacionAceptacionNoMejora);
        }
        return new Resultado(false, par.puntuacionRechazo);
    }

    public double calcularTemperatura(int iteracion) {
        return par.temperaturaInicial * Math.pow(par.factorEnfriamiento, iteracion);
    }
}
