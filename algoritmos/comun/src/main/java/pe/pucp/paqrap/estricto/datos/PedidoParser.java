package pe.pucp.paqrap.estricto.datos;

import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class PedidoParser {
    public Pedido parsear(String linea, int anio, int mes) {
        String[] partes = linea.trim().split(":", -1);
        if (partes.length != 2)
            throw new IllegalArgumentException("Pedido: se esperaba fecha:x,y,id,cantidad,plazo");
        String[] c = partes[1].split(",", -1);
        if (c.length != 5)
            throw new IllegalArgumentException("Pedido: se esperaban cinco campos");
        return new Pedido(c[2].trim(), ParserSupport.momento(partes[0], anio, mes),
                new Nodo(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim())), Integer.parseInt(c[3].trim()),
                Integer.parseInt(c[4].trim()));
    }

    public List<Pedido> leer(Path archivo, int anio, int mes) throws IOException {
        return ParserSupport.leerConLinea(archivo, (s, numero) -> {
            Pedido p = parsear(s, anio, mes);
            // Una fila es un pedido. cIdCliente puede repetirse incluso el mismo dia.
            String id = String.format(java.util.Locale.ROOT, "V%04d%02d-L%05d", anio, mes, numero);
            return new Pedido(id, p.fechaRegistro(), p.ubicacion(), p.cantidad(), p.plazoHoras(), p.clienteId());
        });
    }
}
