package pe.pucp.paqrap.estricto.servicios;

import pe.pucp.paqrap.estricto.modelo.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Insercion comun por plazo; continua aunque una parte no quepa. Solo conserva
 * rutas admisibles.
 */
public final class GeneradorSolucionInicial {
    public static Solucion generar(EvaluadorFactibilidad ev) {
        var e = ev.estado();
        var rs = new ArrayList<Ruta>();
        var pendientes = new ArrayList<>(ev.partes());
        for (var v : e.vehiculos()) {
            var activa = e.rutasEnCurso().stream().filter(r -> r.vehiculo().equals(v.codigo())).findFirst()
                    .orElse(null);
            if (activa != null && ev.evaluarRuta(activa).factible()) {
                rs.add(activa);
                pendientes.removeAll(activa.partes());
            } else
                rs.add(new Ruta(v.codigo(), e.almacenes().get(0).id(), List.of(), false));
        }
        return reparar(new Solucion(rs, pendientes), ev, false, new Random(0));
    }

    public static Solucion reparar(Solucion s, EvaluadorFactibilidad ev, boolean aleatorio, Random rnd) {
        return reparar(s, ev, aleatorio, rnd, () -> true);
    }

    /**
     * Repara mientras quede presupuesto. Si el limite se alcanza durante una
     * insercion, conserva la mejor alternativa factible ya evaluada.
     */
    public static Solucion reparar(Solucion s, EvaluadorFactibilidad ev, boolean aleatorio, Random rnd,
            BooleanSupplier continuar) {
        var orden = new ArrayList<>(s.pendientes());
        if (aleatorio)
            Collections.shuffle(orden, rnd);
        else
            orden.sort(Comparator.comparing((PartePedido p) -> p.pedido().deadline()).thenComparing(PartePedido::id));
        for (var parte : orden) {
            if (!continuar.getAsBoolean())
                return s;
            Solucion mejor = null;
            double costo = Double.POSITIVE_INFINITY;
            for (int i = 0; i < s.rutas().size(); i++) {
                if (!continuar.getAsBoolean())
                    return mejor != null ? mejor : s;
                var r = s.rutas().get(i);
                if (r.enCurso())
                    continue;
                var v = ev.estado().vehiculos().stream().filter(x -> x.codigo().equals(r.vehiculo())).findFirst()
                        .orElseThrow();
                if (!v.disponible() || r.carga() + parte.cantidad() > v.tipo().capacidad())
                    continue;
                List<String> almacenes = r.partes().isEmpty()
                        ? ev.estado().almacenes().stream().map(Almacen::id).toList()
                        : List.of(r.almacenOrigen());
                for (var almacen : almacenes)
                    for (int pos = 0; pos <= r.partes().size(); pos++) {
                        if (!continuar.getAsBoolean())
                            return mejor != null ? mejor : s;
                        var lista = new ArrayList<>(r.partes());
                        lista.add(pos, parte);
                        var rutas = new ArrayList<>(s.rutas());
                        rutas.set(i, new Ruta(r.vehiculo(), almacen, lista, false));
                        var no = new ArrayList<>(s.pendientes());
                        no.remove(parte);
                        var candidata = new Solucion(rutas, no);
                        var evaluacion = ev.evaluar(candidata);
                        if (evaluacion.factible() && evaluacion.objetivo() < costo) {
                            mejor = candidata;
                            costo = evaluacion.objetivo();
                        }
                    }
            }
            if (mejor != null)
                s = mejor;
        }
        return s;
    }

    private GeneradorSolucionInicial() {
    }
}
