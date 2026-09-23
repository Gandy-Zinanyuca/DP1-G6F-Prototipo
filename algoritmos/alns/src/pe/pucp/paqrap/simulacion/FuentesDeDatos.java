package pe.pucp.paqrap.simulacion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

/**
 * Ubicación de los archivos mensuales de entrada.
 *
 * <p>Los archivos del curso siguen una nomenclatura por periodo: {@code ventas.AAAAMM.txt} y
 * {@code bloqueo.AAMM.txt}. A partir de las carpetas donde están, el simulador encuentra el
 * archivo del mes siguiente cuando necesita encadenarlo.</p>
 */
public class FuentesDeDatos {

    private final Path carpetaVentas;
    private final Path carpetaBloqueos;

    /**
     * @param carpetaVentas   carpeta de los archivos {@code ventas.AAAAMM.txt}
     * @param carpetaBloqueos carpeta de los archivos {@code bloqueo.AAMM.txt}, o {@code null}
     *                        para simular sin bloqueos
     */
    public FuentesDeDatos(Path carpetaVentas, Path carpetaBloqueos) {
        this.carpetaVentas = carpetaVentas;
        this.carpetaBloqueos = carpetaBloqueos;
    }

    /** Archivo de ventas del mes, o {@code null} si no existe. */
    public Path ventas(YearMonth mes) {
        Path p = carpetaVentas.resolve(String.format("ventas.%04d%02d.txt",
                mes.getYear(), mes.getMonthValue()));
        return Files.exists(p) ? p : null;
    }

    /** Archivo de bloqueos del mes, o {@code null} si no existe. */
    public Path bloqueos(YearMonth mes) {
        if (carpetaBloqueos == null) {
            return null;
        }
        Path p = carpetaBloqueos.resolve(String.format("bloqueo.%02d%02d.txt",
                mes.getYear() % 100, mes.getMonthValue()));
        return Files.exists(p) ? p : null;
    }
}
