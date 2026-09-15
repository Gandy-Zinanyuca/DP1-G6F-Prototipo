package pe.logistica.data;

import pe.logistica.model.Bloqueo;
import pe.logistica.model.Nodo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BloqueoParser {
    public Bloqueo parsear(String linea, int anio, int mes) {
        String[] partes = linea.trim().split(":", -1);
        if (partes.length != 2) throw new IllegalArgumentException("Bloqueo: se esperaba inicio-fin:coordenadas");
        String[] fechas = partes[0].split("-", -1), c = partes[1].split(",", -1);
        if (fechas.length != 2 || c.length < 4 || c.length % 2 != 0)
            throw new IllegalArgumentException("Bloqueo: intervalo y pares de coordenadas invalidos");
        List<Nodo> nodos = new ArrayList<>();
        for (int i = 0; i < c.length; i += 2)
            nodos.add(new Nodo(Integer.parseInt(c[i].trim()), Integer.parseInt(c[i + 1].trim())));
        return new Bloqueo(ParserSupport.momento(fechas[0], anio, mes),
                ParserSupport.momento(fechas[1], anio, mes), nodos);
    }
    public List<Bloqueo> leer(Path archivo, int anio, int mes) throws IOException {
        return ParserSupport.leer(archivo, s -> parsear(s, anio, mes));
    }
}
