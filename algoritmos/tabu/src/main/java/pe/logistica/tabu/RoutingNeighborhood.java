package pe.logistica.tabu;

import pe.logistica.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

/** Modulo 2: swap intra-ruta; conserva el vehiculo de cada pedido. */
public final class RoutingNeighborhood {
    public void generar(Solucion actual, Consumer<Candidato> consumidor) {
        for (int r = 0; r < actual.rutas().size(); r++) {
            Ruta ruta = actual.rutas().get(r);
            for (int i = 0; i < ruta.pedidos().size(); i++) {
                for (int j = i + 1; j < ruta.pedidos().size(); j++) {
                    var pedidos = new ArrayList<>(ruta.pedidos()); Collections.swap(pedidos, i, j);
                    var rutas = new ArrayList<>(actual.rutas()); rutas.set(r, new Ruta(ruta.vehiculo(), pedidos));
                    consumidor.accept(new Candidato(new Solucion(rutas),
                            TabuMove.swap(ruta.vehiculo().codigo(), ruta.pedidos().get(i).id(), ruta.pedidos().get(j).id())));
                }
            }
        }
    }
}
