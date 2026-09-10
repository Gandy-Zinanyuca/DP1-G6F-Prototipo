package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Remoción del peor (<i>worst removal</i>).
 *
 * <p>Retira los pedidos cuya presencia resulta más cara para la solución actual. La
 * <i>ganancia de remoción</i> de un pedido se estima con el desvío que provoca en su ruta
 * más la tardanza que acumula:</p>
 * <pre>
 *   ganancia(p) = tarifa · [ d(ant, p) + d(p, sig) − d(ant, sig) ]
 *               + w_t · max(0, llegada(p) − límite(p))
 * </pre>
 * <p>Los pedidos con desvío grande son los que están mal colocados geográficamente; los que
 * acumulan tardanza son los que ponen en riesgo el cumplimiento de plazos. Reinsertarlos en
 * otro lugar es lo que más puede mejorar la solución.</p>
 *
 * <h2>Aleatorización del ranking</h2>
 * <p>Tomar siempre los q peores haría el operador determinista y lo llevaría a repetir la misma
 * destrucción una y otra vez. Se aplica la aleatorización de Ropke y Pisinger: ordenados los
 * pedidos por ganancia decreciente, se elige el de índice ⌊y<sup>D</sup>·|L|⌋ con y ~ U(0,1) y
 * D ≥ 1 el factor de sesgo. Con D grande la selección se concentra en los peores; con D = 1 es
 * uniforme. El ranking se calcula una sola vez por llamada, en O(n), y las q extracciones
 * operan sobre él.</p>
 */
public class RemocionPeor implements OperadorDestruccion {

    private final double factorSesgo;

    public RemocionPeor(double factorSesgo) {
        this.factorSesgo = factorSesgo;
    }

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        ParametrosPlanificador par = ctx.getParametros();
        MapaUrbano mapa = ctx.getMapa();
        List<Candidato> ranking = new ArrayList<>();

        for (Ruta r : solucion.getRutas()) {
            if (r.estaVacia()) {
                continue;
            }
            double tarifa = r.getVehiculo().getTipo().getCostoPorKm();
            List<Pedido> sec = r.getSecuencia();
            int[] llegadas = r.getMinutosLlegada();

            for (int i = 0; i < sec.size(); i++) {
                Pedido p = sec.get(i);
                Coordenada anterior = (i == 0)
                        ? r.getAlmacenOrigen().getUbicacion()
                        : sec.get(i - 1).getDestino();
                Coordenada siguiente = (i == sec.size() - 1)
                        ? r.getAlmacenRetorno().getUbicacion()
                        : sec.get(i + 1).getDestino();

                int dEntrada = mapa.distancia(anterior, p.getDestino());
                int dSalida = mapa.distancia(p.getDestino(), siguiente);
                int dDirecta = mapa.distancia(anterior, siguiente);
                double desvio = seguro(dEntrada) + seguro(dSalida) - seguro(dDirecta);

                double tardanza = (i < llegadas.length)
                        ? Math.max(0, llegadas[i] - p.getMinutoLimite()) : 0;

                double ganancia = par.pesoCostoDistancia * tarifa * Math.max(0, desvio)
                        + par.penalizacionPorMinutoTardanza * tardanza;
                ranking.add(new Candidato(p, ganancia));
            }
        }
        if (ranking.isEmpty()) {
            return new ArrayList<>();
        }
        ranking.sort(Comparator.comparingDouble((Candidato c) -> c.ganancia).reversed());

        List<Pedido> removidos = new ArrayList<>();
        int limite = Math.min(q, ranking.size());
        for (int i = 0; i < limite; i++) {
            double y = aleatorio.nextDouble();
            int indice = (int) (Math.pow(y, factorSesgo) * ranking.size());
            indice = Math.min(indice, ranking.size() - 1);

            Pedido elegido = ranking.remove(indice).pedido;
            solucion.desasignar(elegido);
            removidos.add(elegido);
        }
        return removidos;
    }

    /** Evita que una distancia infinita (nodo aislado) contamine la aritmética de la ganancia. */
    private static double seguro(int distancia) {
        return distancia == Integer.MAX_VALUE ? 1_000.0 : distancia;
    }

    @Override
    public String nombre() {
        return "remocion-peor";
    }

    private static final class Candidato {
        final Pedido pedido;
        final double ganancia;

        Candidato(Pedido pedido, double ganancia) {
            this.pedido = pedido;
            this.ganancia = ganancia;
        }
    }
}
