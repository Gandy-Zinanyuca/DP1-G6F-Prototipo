package pe.pucp.paqrap.tabu;

import pe.pucp.paqrap.estricto.modelo.*;
import java.util.*;
import java.util.function.Predicate;

/** Swap y relocate intra-ruta; no altera rutas en curso comprometidas. */
public final class RoutingNeighborhood {
  public void generar(Solucion s, Random random, Predicate<Candidato> aceptar) {
    var indices = new ArrayList<Integer>();
    for (int i = 0; i < s.rutas().size(); i++)
      indices.add(i);
    Collections.shuffle(indices, random);
    for (int r : indices) {
      var ruta = s.rutas().get(r);
      if (ruta.enCurso())
        continue;
      for (int i = 0; i < ruta.partes().size(); i++)
        for (int j = i + 1; j < ruta.partes().size(); j++) {
          var ps = new ArrayList<>(ruta.partes());
          Collections.swap(ps, i, j);
          if (!aceptar.test(new Candidato(s.reemplazar(r, ruta.conPartes(ps)),
              TabuMove.swap(ruta.vehiculo(), ruta.partes().get(i).id(), ruta.partes().get(j).id()))))
            return;
        }
      for (int i = 0; i < ruta.partes().size(); i++)
        for (int j = 0; j < ruta.partes().size(); j++)
          if (i != j) {
            var ps = new ArrayList<>(ruta.partes());
            var parte = ps.remove(i);
            ps.add(j, parte);
            if (!aceptar.test(new Candidato(s.reemplazar(r, ruta.conPartes(ps)),
                TabuMove.relocate(ruta.vehiculo(), parte.id(), i, j))))
              return;
          }
    }
  }
}
