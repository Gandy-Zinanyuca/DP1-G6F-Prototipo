package pe.logistica.tabu;

import pe.logistica.model.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Modulo 1: relocate inter-ruta, probando todas las posiciones de insercion. */
public final class AssignmentNeighborhood {
    public void generar(Solucion actual, Consumer<Candidato> consumidor) {
        for (int i = 0; i < actual.rutas().size(); i++) {
            Ruta origen = actual.rutas().get(i);
            for (int p = 0; p < origen.entregas().size(); p++) {
                Entrega entrega = origen.entregas().get(p);
                for (int j = 0; j < actual.rutas().size(); j++) {
                    if (i == j) continue;
                    Ruta destino = actual.rutas().get(j);
                    // Pseudocodigo 5.1: si la carga de rutaDestino + entrega supera la capacidad
                    // del vehiculo, el candidato no se genera (no se cuenta ni se evalua).
                    if (destino.carga() + entrega.cantidad() > destino.vehiculo().tipo().capacidad()) continue;
                    for (int pos = 0; pos <= destino.entregas().size(); pos++) {
                        var desde = new ArrayList<>(origen.entregas()); desde.remove(p);
                        var hacia = new ArrayList<>(destino.entregas()); hacia.add(pos, entrega);
                        var rutas = new ArrayList<>(actual.rutas());
                        rutas.set(i, new Ruta(origen.vehiculo(), desde)); rutas.set(j, new Ruta(destino.vehiculo(), hacia));
                        consumidor.accept(new Candidato(new Solucion(rutas),
                                TabuMove.asignacion(entrega.pedido().id(), origen.vehiculo().codigo(), destino.vehiculo().codigo())));
                    }
                }
            }
        }
    }
}
