package pe.pucp.paqrap.alns;

import java.util.List;
import java.util.Random;

/**
 * Mecanismo adaptativo del ALNS (ISA 5.2): SELECCIONAR_OPERADOR y
 * ACTUALIZAR_PESOS.
 *
 * <ol>
 * <li>Selección por ruleta: se sortea un valor uniforme en [0, Σ pesos) y se
 * elige el primer operador cuyo peso acumulado lo alcance.</li>
 * <li>Cada operador acumula puntuaciónSegmento y usosSegmento en cada
 * iteración.</li>
 * <li>Al cerrar el segmento: peso ← peso · (1 − r) + r · (puntuaciónSegmento /
 * usosSegmento) para los operadores usados, y los contadores se reinician.</li>
 * </ol>
 *
 * @param <T> tipo de operador administrado (destrucción o reparación)
 */
public class SelectorAdaptativo<T> {

    private final List<T> operadores;
    private final double[] pesos;
    private final double[] puntuacionSegmento;
    private final int[] usosSegmento;
    private final int[] usosAcumulados;
    private final double factorReaccion;

    public SelectorAdaptativo(List<T> operadores, double pesoInicial, double factorReaccion) {
        this.operadores = operadores;
        this.factorReaccion = factorReaccion;
        this.pesos = new double[operadores.size()];
        this.puntuacionSegmento = new double[operadores.size()];
        this.usosSegmento = new int[operadores.size()];
        this.usosAcumulados = new int[operadores.size()];
        java.util.Arrays.fill(pesos, pesoInicial);
    }

    /** SELECCIONAR_OPERADOR: ruleta proporcional al peso. */
    public int seleccionar(Random aleatorio) {
        double sumaPesos = 0;
        for (double w : pesos) {
            sumaPesos += w;
        }
        double valorAleatorio = aleatorio.nextDouble() * sumaPesos;
        double acumulado = 0;
        for (int i = 0; i < pesos.length; i++) {
            acumulado += pesos[i];
            if (valorAleatorio <= acumulado) {
                return i;
            }
        }
        return pesos.length - 1;
    }

    public T operador(int indice) {
        return operadores.get(indice);
    }

    public List<T> getOperadores() {
        return operadores;
    }

    /** Suma la puntuación de la iteración y cuenta un uso del operador. */
    public void puntuar(int indice, double puntuacion) {
        puntuacionSegmento[indice] += puntuacion;
        usosSegmento[indice]++;
        usosAcumulados[indice]++;
    }

    /** ACTUALIZAR_PESOS: cierra el segmento y reinicia los contadores. */
    public void actualizarPesos() {
        for (int i = 0; i < pesos.length; i++) {
            if (usosSegmento[i] > 0) {
                double desempenioSegmento = puntuacionSegmento[i] / usosSegmento[i];
                pesos[i] = pesos[i] * (1 - factorReaccion) + factorReaccion * desempenioSegmento;
            }
            puntuacionSegmento[i] = 0;
            usosSegmento[i] = 0;
        }
    }

    public double[] getPesos() {
        return pesos.clone();
    }

    public int[] getUsosAcumulados() {
        return usosAcumulados.clone();
    }
}
