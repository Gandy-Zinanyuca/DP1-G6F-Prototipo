package pe.pucp.paqrap.datos;

import pe.pucp.paqrap.modelo.Bloqueo;
import pe.pucp.paqrap.modelo.Coordenada;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector del archivo mensual de bloqueos de calles (LE073, LE075, LE081).
 *
 * <p>
 * Formato de línea: {@code DDdHHhMMm-DDdHHhMMm:x1,y1,x2,y2,...,xn,yn}, por
 * ejemplo {@code 01d02h22m-01d04h42m:25,45,45,45,45,40}. El primer campo es el
 * intervalo de vigencia y el segundo una polilínea de nodos cuyos tramos quedan
 * cerrados en ambos sentidos.
 * </p>
 *
 * <p>
 * Se rechazan las líneas con número impar de coordenadas, con vértices fuera de
 * la retícula (LE081) o con intervalo invertido, informando el motivo sin
 * interrumpir la carga del resto del archivo.
 * </p>
 */
public final class CargadorBloqueos {

    public static class Resultado {
        public final List<Bloqueo> bloqueos;
        public final int omitidos;
        public final List<String> motivos;

        Resultado(List<Bloqueo> bloqueos, int omitidos, List<String> motivos) {
            this.bloqueos = bloqueos;
            this.omitidos = omitidos;
            this.motivos = motivos;
        }
    }

    private CargadorBloqueos() {
    }

    public static Resultado cargar(Path archivo) throws IOException {
        return cargar(archivo, 0);
    }

    /**
     * Carga un archivo mensual cuyo día 1, 00:00, corresponde al minuto
     * {@code desplazamiento} del reloj de la simulación; se usa al encadenar meses.
     */
    public static Resultado cargar(Path archivo, int desplazamiento) throws IOException {
        List<Bloqueo> bloqueos = new ArrayList<>();
        List<String> motivos = new ArrayList<>();
        int omitidos = 0;
        int numeroLinea = 0;

        try (BufferedReader br = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = br.readLine()) != null) {
                numeroLinea++;
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                try {
                    bloqueos.add(parsear(linea, desplazamiento));
                } catch (RuntimeException e) {
                    omitidos++;
                    motivos.add("Línea " + numeroLinea + ": " + e.getMessage());
                }
            }
        }
        return new Resultado(bloqueos, omitidos, motivos);
    }

    static Bloqueo parsear(String linea, int desplazamiento) {
        int sep = linea.indexOf(':');
        if (sep < 0) {
            throw new IllegalArgumentException("falta el separador ':'");
        }
        String intervalo = linea.substring(0, sep);
        int guion = intervalo.indexOf('-');
        if (guion < 0) {
            throw new IllegalArgumentException("intervalo sin guion separador");
        }
        int inicio = desplazamiento + CargadorVentas.parsearInstante(intervalo.substring(0, guion));
        int fin = desplazamiento + CargadorVentas.parsearInstante(intervalo.substring(guion + 1));
        if (fin < inicio) {
            throw new IllegalArgumentException("intervalo invertido");
        }

        String[] campos = linea.substring(sep + 1).split(",");
        if (campos.length < 4 || campos.length % 2 != 0) {
            throw new IllegalArgumentException("polilínea con número inválido de coordenadas");
        }
        List<Coordenada> vertices = new ArrayList<>(campos.length / 2);
        for (int i = 0; i < campos.length; i += 2) {
            Coordenada c = new Coordenada(Integer.parseInt(campos[i].trim()), Integer.parseInt(campos[i + 1].trim()));
            if (!c.dentroDelMapa()) {
                throw new IllegalArgumentException("vértice fuera de la retícula: " + c);
            }
            vertices.add(c);
        }
        return new Bloqueo(inicio, fin, vertices);
    }
}
