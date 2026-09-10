package pe.pucp.paqrap.alns.destruccion;

import pe.pucp.paqrap.alns.OperadorDestruccion;
import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * <b>Operador propio del dominio</b>: remoción por arco bloqueado (<i>blocked-arc removal</i>).
 *
 * <p>Retira los pedidos cuyos tramos de ruta atraviesan una calle cerrada en el instante de
 * planificación. Es la traducción algorítmica directa del mecanismo de recuperación que el
 * enunciado describe: ante un bloqueo, PaqRap no reoptimiza toda la operación, sino que
 * reasigna únicamente los productos en camino que quedaron comprometidos.</p>
 *
 * <p>La detección aprovecha una propiedad de la retícula: sin bloqueos, la distancia mínima
 * entre dos nodos es exactamente la distancia Manhattan. Por lo tanto, un tramo cuya distancia
 * real supera a la Manhattan es un tramo que fue desviado por un cierre, y el pedido en su
 * extremo es candidato a remoción. La comprobación cuesta O(1) por tramo.</p>
 *
 * <p>Si los bloqueos vigentes no afectan a ninguna ruta, el operador completa el grado de
 * destrucción con pedidos elegidos al azar, de modo que nunca devuelve una destrucción vacía
 * que desperdiciaría la iteración.</p>
 */
public class RemocionPorArcoBloqueado implements OperadorDestruccion {

    @Override
    public List<Pedido> destruir(Solucion solucion, int q, ContextoPlanificacion ctx, Random aleatorio) {
        MapaUrbano mapa = ctx.getMapa();
        List<Pedido> afectados = new ArrayList<>();

        if (!mapa.getBloqueosVigentes().isEmpty()) {
            for (Ruta r : solucion.getRutas()) {
                if (r.estaVacia()) {
                    continue;
                }
                Coordenada anterior = r.getAlmacenOrigen().getUbicacion();
                for (Pedido p : r.getSecuencia()) {
                    if (mapa.rutaAfectadaPorBloqueo(anterior, p.getDestino())) {
                        afectados.add(p);
                    }
                    anterior = p.getDestino();
                }
            }
        }
        Collections.shuffle(afectados, aleatorio);

        List<Pedido> removidos = new ArrayList<>();
        for (Pedido p : afectados) {
            if (removidos.size() >= q) {
                break;
            }
            solucion.desasignar(p);
            removidos.add(p);
        }

        // Relleno aleatorio si los bloqueos no comprometieron suficientes pedidos.
        if (removidos.size() < q) {
            List<Pedido> resto = new ArrayList<>(solucion.pedidosAsignados());
            Collections.shuffle(resto, aleatorio);
            for (Pedido p : resto) {
                if (removidos.size() >= q) {
                    break;
                }
                solucion.desasignar(p);
                removidos.add(p);
            }
        }
        return removidos;
    }

    @Override
    public String nombre() {
        return "remocion-arco-bloqueado";
    }
}
