package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Remoción por relación (<i>Shaw removal</i>).
 *
 * <p>Retira un conjunto de pedidos <b>parecidos entre sí</b>. La intuición es que reinsertar
 * pedidos similares tiene sentido: si son geográficamente vecinos, comparten hora límite y
 * caben en las mismas unidades, es probable que exista una recombinación mejor entre ellos.
 * Remover pedidos sin relación, en cambio, casi siempre los devuelve al mismo lugar.</p>
 *
 * <p>La medida de relación entre dos pedidos combina tres términos normalizados:</p>
 * <pre>
 *   R(i,j) = φ · d(i,j)/d_max  +  χ · |límite_i − límite_j|/T_max  +  ψ · |q_i − q_j|/q_max
 * </pre>
 * <p>Un valor bajo de R significa alta relación. El procedimiento arranca de un pedido semilla
 * elegido al azar y va incorporando, en cada paso, uno de los más relacionados con alguno de
 * los ya removidos, usando la misma aleatorización sesgada del operador del peor para no
 * volverse determinista.</p>
 */
public class RemocionRelacionadaShaw implements OperadorDestruccion {

    private final double pesoDistancia;
    private final double pesoTiempo;
    private final double pesoCarga;
    private final double factorSesgo;

    public RemocionRelacionadaShaw(double pesoDistancia, double pesoTiempo,
                                   double pesoCarga, double factorSesgo) {
        this.pesoDistancia = pesoDistancia;
        this.pesoTiempo = pesoTiempo;
        this.pesoCarga = pesoCarga;
        this.factorSesgo = factorSesgo;
    }

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        MapaUrbano mapa = ctx.getMapa();
        List<Pedido> disponibles = new ArrayList<>(solucion.pedidosAsignados());
        List<Pedido> removidos = new ArrayList<>();
        if (disponibles.isEmpty()) {
            return removidos;
        }

        // Normalizadores para que los tres términos sean comparables entre sí.
        final double dMax = pe.pucp.paqrap.modelo.Coordenada.ANCHO_MAX
                + pe.pucp.paqrap.modelo.Coordenada.ALTO_MAX;
        int limiteMin = Integer.MAX_VALUE;
        int limiteMax = Integer.MIN_VALUE;
        double qMax = 1;
        for (Pedido a : disponibles) {
            qMax = Math.max(qMax, a.getCantidad());
            limiteMin = Math.min(limiteMin, a.getMinutoLimite());
            limiteMax = Math.max(limiteMax, a.getMinutoLimite());
        }
        double tMax = Math.max(1, limiteMax - limiteMin);

        // Semilla aleatoria.
        Pedido semilla = disponibles.remove(aleatorio.nextInt(disponibles.size()));
        solucion.desasignar(semilla);
        removidos.add(semilla);

        while (removidos.size() < q && !disponibles.isEmpty()) {
            Pedido referencia = removidos.get(aleatorio.nextInt(removidos.size()));
            final double fdMax = dMax;
            final double ftMax = tMax;
            final double fqMax = qMax;

            disponibles.sort(Comparator.comparingDouble(
                    p -> relacion(referencia, p, mapa, fdMax, ftMax, fqMax)));

            double y = aleatorio.nextDouble();
            int indice = (int) (Math.pow(y, factorSesgo) * disponibles.size());
            indice = Math.min(indice, disponibles.size() - 1);

            Pedido elegido = disponibles.remove(indice);
            solucion.desasignar(elegido);
            removidos.add(elegido);
        }
        return removidos;
    }

    private double relacion(Pedido a, Pedido b, MapaUrbano mapa,
                            double dMax, double tMax, double qMax) {
        int d = mapa.distancia(a.getDestino(), b.getDestino());
        double dNorm = (d == Integer.MAX_VALUE ? 1.0 : d / dMax);
        double tNorm = Math.abs(a.getMinutoLimite() - b.getMinutoLimite()) / tMax;
        double qNorm = Math.abs(a.getCantidad() - b.getCantidad()) / qMax;
        return pesoDistancia * dNorm + pesoTiempo * tNorm + pesoCarga * qNorm;
    }

    @Override
    public String nombre() {
        return "remocion-shaw";
    }
}
