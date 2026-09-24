package pe.pucp.paqrap.simulacion;

import pe.pucp.paqrap.modelo.Turnos;
import pe.pucp.paqrap.modelo.Vehiculo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Verificación independiente de la hora de alimentación (LE018) sobre lo que la simulación
 * efectivamente despachó.
 *
 * <p>Por cada turno completo del periodo simulado y cada unidad, el chofer cumplió si tomó una
 * comida planificada que empieza dentro de la ventana del turno ([inicio + 1 h, fin − 2 h]), o si
 * tuvo en esa ventana una hora libre —sin viajes despachados— en la que pudo comer (unidad ociosa o
 * de regreso en el almacén). En otro caso el turno queda <b>incumplido</b>: la unidad estuvo
 * ocupada durante toda la ventana sin hora de comida.</p>
 *
 * <p>Además cuenta en qué hora del turno empiezan las comidas planificadas, para ver que las
 * unidades no comen todas a la vez.</p>
 */
public class AuditoriaAlimentacion {

    /** Intervalos ocupados {salida, fin} de los viajes despachados, por unidad. */
    private final Map<String, List<int[]>> ocupado = new LinkedHashMap<>();
    /** Inicios de las comidas planificadas en viajes despachados, por unidad. */
    private final Map<String, List<Integer>> comidas = new LinkedHashMap<>();

    public int turnosEvaluados;
    public int comioEnRuta;
    public int comioEnTiempoLibre;
    public int incumplidos;
    public final List<String> ejemplosIncumplidos = new ArrayList<>();
    /** Comidas planificadas según la hora del turno en que empiezan (0 = primera hora). */
    public final int[] comidasPorHoraDelTurno = new int[Turnos.DURACION_TURNO_MIN / 60];

    public void registrarViaje(String unidad, int salida, int fin) {
        ocupado.computeIfAbsent(unidad, k -> new ArrayList<>()).add(new int[]{salida, fin});
    }

    public void registrarComida(String unidad, int inicio) {
        comidas.computeIfAbsent(unidad, k -> new ArrayList<>()).add(inicio);
        comidasPorHoraDelTurno[(inicio - Turnos.inicioTurno(inicio)) / 60]++;
    }

    /** Evalúa los turnos que empiezan y terminan dentro de [desde, hasta]. */
    public void evaluar(List<Vehiculo> flota, int desde, int hasta, IntFunction<String> formatear) {
        turnosEvaluados = comioEnRuta = comioEnTiempoLibre = incumplidos = 0;
        ejemplosIncumplidos.clear();
        int primero = Turnos.inicioTurno(desde);
        if (primero < desde) {
            primero += Turnos.DURACION_TURNO_MIN;
        }
        for (int turno = primero; turno + Turnos.DURACION_TURNO_MIN <= hasta;
             turno += Turnos.DURACION_TURNO_MIN) {
            int ventanaIni = turno + Turnos.SEPARACION_CAMBIO_TURNO_MIN;
            int ventanaFin = turno + Turnos.DURACION_TURNO_MIN - Turnos.SEPARACION_CAMBIO_TURNO_MIN
                    - Turnos.DURACION_ALMUERZO_MIN;
            for (Vehiculo v : flota) {
                turnosEvaluados++;
                if (comioEnVentana(v.getCodigo(), ventanaIni, ventanaFin)) {
                    comioEnRuta++;
                } else if (horaLibre(v.getCodigo(), ventanaIni, ventanaFin)) {
                    comioEnTiempoLibre++;
                } else {
                    incumplidos++;
                    if (ejemplosIncumplidos.size() < 5) {
                        ejemplosIncumplidos.add(v.getCodigo() + " en el turno de las " + formatear.apply(turno));
                    }
                }
            }
        }
    }

    private boolean comioEnVentana(String unidad, int ventanaIni, int ventanaFin) {
        for (int inicio : comidas.getOrDefault(unidad, List.of())) {
            if (inicio >= ventanaIni && inicio <= ventanaFin) {
                return true;
            }
        }
        return false;
    }

    /** Hay una hora sin viajes que empieza dentro de la ventana. */
    private boolean horaLibre(String unidad, int ventanaIni, int ventanaFin) {
        List<int[]> intervalos = new ArrayList<>();
        for (int[] i : ocupado.getOrDefault(unidad, List.of())) {
            if (i[1] > ventanaIni && i[0] < ventanaFin + Turnos.DURACION_ALMUERZO_MIN) {
                intervalos.add(i);
            }
        }
        intervalos.sort((a, b) -> Integer.compare(a[0], b[0]));
        int libreDesde = ventanaIni;
        for (int[] i : intervalos) {
            if (libreDesde <= ventanaFin && i[0] - libreDesde >= Turnos.DURACION_ALMUERZO_MIN) {
                return true;
            }
            libreDesde = Math.max(libreDesde, i[1]);
        }
        return libreDesde <= ventanaFin;
    }

    @Override
    public String toString() {
        StringBuilder horas = new StringBuilder();
        for (int h = 0; h < comidasPorHoraDelTurno.length; h++) {
            horas.append(h == 0 ? "" : " · ").append('h').append(h + 1).append('=').append(comidasPorHoraDelTurno[h]);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(" Alimentación (LE018) : %d turnos-unidad · %d comió en ruta · %d en tiempo libre"
                + " · %d INCUMPLIDOS%n", turnosEvaluados, comioEnRuta, comioEnTiempoLibre, incumplidos));
        sb.append(String.format("   inicio de las comidas en ruta por hora del turno: %s%n", horas));
        if (!ejemplosIncumplidos.isEmpty()) {
            sb.append(String.format("   incumplidos, p. ej.: %s%n", String.join("; ", ejemplosIncumplidos)));
        }
        return sb.toString();
    }
}
