package pe.pucp.paqrap.datos;

import pe.pucp.paqrap.modelo.Coordenada;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Turnos;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector del archivo mensual de ventas (LE003, LE009).
 *
 * <p>Formato de línea: {@code DDdHHhMMm:x,y,cCCCC,cantidad,plazo}, por ejemplo
 * {@code 01d01h30m:56,30,c4910,02,36}. Los campos son, en orden: instante de registro del
 * pedido, coordenadas del nodo de destino, código de cliente, cantidad de producto P y plazo
 * comprometido en horas (36 regular; 4, 8, 12 o 18 priorizado).</p>
 *
 * <p>La lectura es determinista: los pedidos se numeran de forma correlativa en el orden en
 * que aparecen en el archivo, de modo que dos ejecuciones sobre la misma entrada producen los
 * mismos identificadores (LE008, LE009). Las líneas malformadas se omiten y se contabilizan
 * en {@link Resultado#omitidos} sin abortar la carga (LE011).</p>
 */
public final class CargadorVentas {

    /** Resultado de la carga: pedidos válidos y conteo de líneas descartadas. */
    public static class Resultado {
        public final List<Pedido> pedidos;
        public final int omitidos;
        public final List<String> motivos;

        Resultado(List<Pedido> pedidos, int omitidos, List<String> motivos) {
            this.pedidos = pedidos;
            this.omitidos = omitidos;
            this.motivos = motivos;
        }
    }

    private CargadorVentas() {
    }

    public static Resultado cargar(Path archivo) throws IOException {
        return cargar(archivo, 0, 1);
    }

    /**
     * Carga un archivo mensual cuyo día 1, 00:00, corresponde al minuto {@code desplazamiento}
     * del reloj de la simulación; se usa al encadenar meses. Los pedidos se numeran desde
     * {@code idInicial} para que los identificadores no se repitan entre meses.
     */
    public static Resultado cargar(Path archivo, int desplazamiento, int idInicial) throws IOException {
        List<Pedido> pedidos = new ArrayList<>();
        List<String> motivos = new ArrayList<>();
        int omitidos = 0;
        int siguienteId = idInicial;
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
                    pedidos.add(parsear(linea, siguienteId, desplazamiento));
                    siguienteId++;
                } catch (RuntimeException e) {
                    omitidos++;
                    motivos.add("Línea " + numeroLinea + ": " + e.getMessage());
                }
            }
        }
        return new Resultado(pedidos, omitidos, motivos);
    }

    /** Parsea una línea del archivo de ventas al modelo de pedido. */
    static Pedido parsear(String linea, int id, int desplazamiento) {
        int sep = linea.indexOf(':');
        if (sep < 0) {
            throw new IllegalArgumentException("falta el separador ':'");
        }
        int minutoRegistro = desplazamiento + parsearInstante(linea.substring(0, sep));
        String[] campos = linea.substring(sep + 1).split(",");
        if (campos.length != 5) {
            throw new IllegalArgumentException("se esperaban 5 campos, hay " + campos.length);
        }
        int x = Integer.parseInt(campos[0].trim());
        int y = Integer.parseInt(campos[1].trim());
        Coordenada destino = new Coordenada(x, y);
        if (!destino.dentroDelMapa()) {
            throw new IllegalArgumentException("destino fuera de la retícula: " + destino);
        }
        String cliente = campos[2].trim();
        int cantidad = Integer.parseInt(campos[3].trim());
        if (cantidad <= 0) {
            throw new IllegalArgumentException("cantidad no positiva: " + cantidad);
        }
        int plazo = Integer.parseInt(campos[4].trim());
        if (plazo != 4 && plazo != 8 && plazo != 12 && plazo != 18 && plazo != 36) {
            throw new IllegalArgumentException("plazo no admitido: " + plazo);
        }
        return new Pedido(id, cliente, destino, cantidad, minutoRegistro, plazo);
    }

    /** Convierte el sello de tiempo {@code DDdHHhMMm} a minutos absolutos de simulación. */
    static int parsearInstante(String sello) {
        String s = sello.trim();
        int posD = s.indexOf('d');
        int posH = s.indexOf('h');
        int posM = s.indexOf('m');
        if (posD < 0 || posH < 0 || posM < 0) {
            throw new IllegalArgumentException("sello de tiempo inválido: " + sello);
        }
        int dia = Integer.parseInt(s.substring(0, posD));
        int hora = Integer.parseInt(s.substring(posD + 1, posH));
        int minuto = Integer.parseInt(s.substring(posH + 1, posM));
        return Turnos.aMinutos(dia, hora, minuto);
    }
}
