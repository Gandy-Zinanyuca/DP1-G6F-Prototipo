package pe.pucp.paqrap.alns;

import java.util.List;
import java.util.Random;

/**
 * Mecanismo adaptativo del ALNS: selección de operadores por ruleta con pesos que se
 * actualizan según el desempeño reciente.
 *
 * <p>Es el rasgo que distingue al ALNS del Large Neighborhood Search clásico y la razón por la
 * que una misma implementación puede ajustarse a los tres escenarios de evaluación sin
 * reprogramarse: los operadores útiles en la operación día a día no son los mismos que dominan
 * cerca del colapso, y el algoritmo lo descubre solo.</p>
 *
 * <h2>Mecánica</h2>
 * <ol>
 *   <li>En cada iteración se elige el operador <i>i</i> con probabilidad
 *       p<sub>i</sub> = w<sub>i</sub> / Σ w<sub>j</sub> (ruleta).</li>
 *   <li>Se acumula un puntaje π<sub>i</sub> según el resultado obtenido y se cuenta el uso
 *       θ<sub>i</sub>.</li>
 *   <li>Al terminar un segmento de iteraciones, los pesos se suavizan:
 *       w<sub>i</sub> ← λ·w<sub>i</sub> + (1−λ)·(π<sub>i</sub>/θ<sub>i</sub>),
 *       y los contadores se reinician.</li>
 * </ol>
 *
 * <p>El factor de reacción λ ∈ [0,1] controla la memoria: valores altos conservan el
 * aprendizaje acumulado, valores bajos reaccionan rápido al desempeño del último segmento.
 * Los pesos se acotan por abajo para que ningún operador quede permanentemente excluido, lo que
 * preserva la capacidad de diversificación en fases tardías de la búsqueda.</p>
 *
 * @param <T> tipo de operador administrado (destrucción o reparación)
 */
public class SelectorAdaptativo<T> {

    private static final double PESO_MINIMO = 0.05;

    private final List<T> operadores;
    private final double[] pesos;
    private final double[] puntajes;
    private final int[] usos;
    private final int[] usosAcumulados;
    private final double factorReaccion;

    public SelectorAdaptativo(List<T> operadores, double factorReaccion) {
        this.operadores = operadores;
        this.factorReaccion = factorReaccion;
        this.pesos = new double[operadores.size()];
        this.puntajes = new double[operadores.size()];
        this.usos = new int[operadores.size()];
        this.usosAcumulados = new int[operadores.size()];
        java.util.Arrays.fill(pesos, 1.0);
    }

    /** Selecciona un operador por ruleta proporcional a su peso. */
    public int seleccionar(Random aleatorio) {
        double total = 0;
        for (double w : pesos) {
            total += w;
        }
        double corte = aleatorio.nextDouble() * total;
        double acumulado = 0;
        for (int i = 0; i < pesos.length; i++) {
            acumulado += pesos[i];
            if (acumulado >= corte) {
                registrarUso(i);
                return i;
            }
        }
        int ultimo = pesos.length - 1;
        registrarUso(ultimo);
        return ultimo;
    }

    private void registrarUso(int indice) {
        usos[indice]++;
        usosAcumulados[indice]++;
    }

    public T operador(int indice) {
        return operadores.get(indice);
    }

    public List<T> getOperadores() {
        return operadores;
    }

    /** Acumula el puntaje obtenido por el operador en la iteración actual. */
    public void premiar(int indice, double puntaje) {
        puntajes[indice] += puntaje;
    }

    /** Cierra el segmento: suaviza los pesos con el desempeño observado y reinicia contadores. */
    public void actualizarPesos() {
        for (int i = 0; i < pesos.length; i++) {
            if (usos[i] > 0) {
                double desempenio = puntajes[i] / usos[i];
                pesos[i] = factorReaccion * pesos[i] + (1 - factorReaccion) * desempenio;
                pesos[i] = Math.max(PESO_MINIMO, pesos[i]);
            }
            puntajes[i] = 0;
            usos[i] = 0;
        }
    }

    public double[] getPesos() {
        return pesos.clone();
    }

    public int[] getUsosAcumulados() {
        return usosAcumulados.clone();
    }
}
