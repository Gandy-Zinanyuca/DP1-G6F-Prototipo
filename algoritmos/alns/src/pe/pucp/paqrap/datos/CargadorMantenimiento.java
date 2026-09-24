package pe.pucp.paqrap.datos;

import pe.pucp.paqrap.modelo.Mantenimiento;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector del archivo de mantenimiento preventivo.
 *
 * <p>
 * Formato de línea: {@code AAAAMMDD:TXNN}, por ejemplo {@code 20260901:TA01}.
 * Cada línea declara que la unidad indicada queda fuera de servicio durante
 * todo ese día, por lo que el planificador la excluye del conjunto de unidades
 * asignables de la jornada.
 * </p>
 */
public final class CargadorMantenimiento {

    private CargadorMantenimiento() {
    }

    public static List<Mantenimiento> cargar(Path archivo) throws IOException {
        List<Mantenimiento> lista = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                int sep = linea.indexOf(':');
                if (sep != 8) {
                    continue;
                }
                String fecha = linea.substring(0, sep);
                String unidad = linea.substring(sep + 1).trim();
                lista.add(new Mantenimiento(Integer.parseInt(fecha.substring(0, 4)),
                        Integer.parseInt(fecha.substring(4, 6)), Integer.parseInt(fecha.substring(6, 8)), unidad));
            }
        }
        return lista;
    }
}
