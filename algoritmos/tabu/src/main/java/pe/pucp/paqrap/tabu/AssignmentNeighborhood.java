package pe.pucp.paqrap.tabu;

import pe.pucp.paqrap.estricto.modelo.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * Transferencia inter-ruta e insercion/retiro de pendientes. Reutiliza el
 * esquema de ambos intentos.
 */
public final class AssignmentNeighborhood {
    public void generar(Solucion s, EstadoOperacion e, Random random, Predicate<Candidato> aceptar) {
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < s.rutas().size(); i++)
            indices.add(i);
        Collections.shuffle(indices, random);
        // Se mezclan partes asignadas y pendientes para evitar agotar el presupuesto
        // solo en pendientes.
        var partes = new ArrayList<PartePedido>(s.pendientes());
        for (var r : s.rutas())
            if (!r.enCurso())
                partes.addAll(r.partes());
        Collections.shuffle(partes, random);
        for (var parte : partes) {
            int origen = -1;
            for (int i = 0; i < s.rutas().size(); i++)
                if (s.rutas().get(i).partes().contains(parte)) {
                    origen = i;
                    break;
                }
            String desde = origen < 0 ? "PENDIENTE" : s.rutas().get(origen).vehiculo();
            if (origen >= 0) {
                var rs = new ArrayList<>(s.rutas());
                var lista = new ArrayList<>(rs.get(origen).partes());
                lista.remove(parte);
                rs.set(origen, rs.get(origen).conPartes(lista));
                var no = new ArrayList<>(s.pendientes());
                no.add(parte);
                if (!aceptar
                        .test(new Candidato(new Solucion(rs, no), TabuMove.asignacion(parte.id(), desde, "PENDIENTE"))))
                    return;
            }
            for (int destino : indices) {
                if (destino == origen)
                    continue;
                var ruta = s.rutas().get(destino);
                if (ruta.enCurso())
                    continue;
                var v = e.vehiculos().stream().filter(x -> x.codigo().equals(ruta.vehiculo())).findFirst()
                        .orElseThrow();
                if (!v.disponible() || ruta.carga() + parte.cantidad() > v.tipo().capacidad())
                    continue;
                var almacenes = ruta.partes().isEmpty() ? e.almacenes().stream().map(Almacen::id).toList()
                        : List.of(ruta.almacenOrigen());
                for (var almacen : almacenes)
                    for (int pos = 0; pos <= ruta.partes().size(); pos++) {
                        var rs = new ArrayList<>(s.rutas());
                        var no = new ArrayList<>(s.pendientes());
                        if (origen < 0)
                            no.remove(parte);
                        else {
                            var lista = new ArrayList<>(rs.get(origen).partes());
                            lista.remove(parte);
                            rs.set(origen, rs.get(origen).conPartes(lista));
                        }
                        var lista = new ArrayList<>(ruta.partes());
                        lista.add(pos, parte);
                        rs.set(destino, new Ruta(ruta.vehiculo(), almacen, lista, false));
                        if (!aceptar.test(new Candidato(new Solucion(rs, no),
                                TabuMove.asignacion(parte.id(), desde, ruta.vehiculo()))))
                            return;
                    }
            }
        }
    }
}
