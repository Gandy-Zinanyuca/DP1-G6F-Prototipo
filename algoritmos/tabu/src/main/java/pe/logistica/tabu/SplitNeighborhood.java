package pe.logistica.tabu;

import pe.logistica.model.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Modulo 3: fracciona una entrega entre dos rutas cuando ninguna sola tiene espacio para el total. */
public final class SplitNeighborhood {
    public void generar(Solucion actual, Consumer<Candidato> consumidor) {
        for (int i = 0; i < actual.rutas().size(); i++) {
            Ruta origen = actual.rutas().get(i);
            for (int p = 0; p < origen.entregas().size(); p++) {
                Entrega entrega = origen.entregas().get(p);
                if (entrega.cantidad() <= 1) continue; // no se puede fraccionar mas
                for (int j = 0; j < actual.rutas().size(); j++) {
                    if (i == j) continue;
                    Ruta destino = actual.rutas().get(j);
                    int libre = destino.vehiculo().tipo().capacidad() - destino.carga();
                    // libre<=0: no cabe nada. libre>=cantidad: ya lo cubre AssignmentNeighborhood (relocate completo).
                    if (libre <= 0 || libre >= entrega.cantidad()) continue;
                    int quedaEnOrigen = entrega.cantidad() - libre;
                    for (int pos = 0; pos <= destino.entregas().size(); pos++) {
                        var entregasOrigen = new ArrayList<>(origen.entregas());
                        entregasOrigen.set(p, new Entrega(entrega.pedido(), quedaEnOrigen));
                        var entregasDestino = new ArrayList<>(destino.entregas());
                        entregasDestino.add(pos, new Entrega(entrega.pedido(), libre));
                        var rutas = new ArrayList<>(actual.rutas());
                        rutas.set(i, new Ruta(origen.vehiculo(), entregasOrigen));
                        rutas.set(j, new Ruta(destino.vehiculo(), entregasDestino));
                        consumidor.accept(new Candidato(new Solucion(rutas),
                                TabuMove.asignacion(entrega.pedido().id(), origen.vehiculo().codigo(), destino.vehiculo().codigo())));
                    }
                }
            }
        }
    }
}
