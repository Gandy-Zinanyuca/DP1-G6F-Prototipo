package pe.pucp.paqrap.estricto.servicios;

import pe.pucp.paqrap.estricto.modelo.*;
import java.util.*;

/**
 * Conserva las partes de rutas en curso y divide el resto sin perder
 * cantidades.
 */
public final class GestorCapacidad {
    public static List<PartePedido> dividir(EstadoOperacion e, ParametrosOperacion p) {
        var partes = new ArrayList<PartePedido>();
        var cantidades = new HashMap<String, Integer>();
        var ids = new HashSet<String>();
        var pedidos = new HashMap<String, Pedido>();
        for (var pedido : e.pedidos())
            pedidos.put(pedido.id(), pedido);
        int tamanio = Math.min(p.tamanioParte(),
                e.vehiculos().stream().mapToInt(v -> v.tipo().capacidad()).min().orElse(p.tamanioParte()));
        for (var r : e.rutasEnCurso())
            for (var parte : r.partes()) {
                if (!parte.pedido().equals(pedidos.get(parte.pedido().id())) || !ids.add(parte.id()))
                    throw new IllegalArgumentException("Parte en curso duplicada o ajena");
                cantidades.merge(parte.pedido().id(), parte.cantidad(), Math::addExact);
                partes.add(parte);
            }
        for (var pedido : e.pedidos()) {
            int restante = pedido.cantidad() - cantidades.getOrDefault(pedido.id(), 0);
            if (restante < 0)
                throw new IllegalArgumentException("Carga en curso excede pedido");
            int i = 0;
            while (restante > 0) {
                String id = pedido.id() + "#" + (++i);
                if (!ids.add(id))
                    continue;
                int cantidad = Math.min(restante, tamanio);
                partes.add(new PartePedido(id, pedido, cantidad));
                restante -= cantidad;
            }
        }
        return List.copyOf(partes);
    }

    private GestorCapacidad() {
    }
}
