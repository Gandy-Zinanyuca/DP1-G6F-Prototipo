package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Eliminación relacionada (ISA 5.2).
 *
 * <pre>
 * pedidoSemilla ← pedido al azar de parcial
 * candidatos    ← pedidos de parcial distintos de la semilla, ordenados por relación con ella
 *                 (cercanía de ubicación, proximidad de deadline, pertenencia a la misma ruta)
 * REPETIR grado veces: retirar el siguiente candidato más relacionado
 * </pre>
 *
 * <p>La relación se mide como suma de tres términos normalizados en [0,1]; un valor bajo indica
 * alta relación: distancia de retícula / (70+50), |Δ deadline| / rango de deadlines, y 0 si
 * comparte ruta con la semilla o 1 si no. La semilla cuenta como el primer pedido removido.</p>
 */
public class RemocionRelacionadaShaw implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        List<Pedido> candidatos = new ArrayList<>(solucion.pedidosAsignados());
        List<Pedido> removidos = new ArrayList<>();
        if (candidatos.isEmpty() || q <= 0) {
            return removidos;
        }

        Pedido semilla = candidatos.remove(aleatorio.nextInt(candidatos.size()));
        final String rutaSemilla = solucion.unidadDe(semilla);

        int limiteMin = semilla.getMinutoLimite();
        int limiteMax = semilla.getMinutoLimite();
        for (Pedido p : candidatos) {
            limiteMin = Math.min(limiteMin, p.getMinutoLimite());
            limiteMax = Math.max(limiteMax, p.getMinutoLimite());
        }
        final double dMax = Coordenada.ANCHO_MAX + Coordenada.ALTO_MAX;
        final double tMax = Math.max(1, limiteMax - limiteMin);

        candidatos.sort(Comparator
                .comparingDouble((Pedido p) -> relacion(semilla, rutaSemilla, p, solucion, dMax, tMax))
                .thenComparingInt(Pedido::getId));

        solucion.desasignar(semilla);
        removidos.add(semilla);
        for (Pedido p : candidatos) {
            if (removidos.size() >= q) {
                break;
            }
            solucion.desasignar(p);
            removidos.add(p);
        }
        return removidos;
    }

    private static double relacion(Pedido semilla, String rutaSemilla, Pedido p, Solucion s,
                                   double dMax, double tMax) {
        double distancia = semilla.getDestino().distanciaManhattan(p.getDestino()) / dMax;
        double deadline = Math.abs(semilla.getMinutoLimite() - p.getMinutoLimite()) / tMax;
        double mismaRuta = rutaSemilla != null && rutaSemilla.equals(s.unidadDe(p)) ? 0.0 : 1.0;
        return distancia + deadline + mismaRuta;
    }

    @Override
    public String nombre() {
        return "eliminacion-relacionada";
    }
}
