package pe.pucp.paqrap.modelo;

/**
 * Utilidades de calendario de la simulación y reglas de jornada laboral.
 *
 * <p>
 * El reloj de la simulación se expresa en minutos enteros desde el día 01 a las
 * 00:00. Los cambios de turno ocurren a las 07:00, 15:00 y 23:00 (LE017, LE069)
 * y cada unidad debe reservar una hora de alimentación dentro de su jornada,
 * ubicada al menos una hora antes o una hora después de un cambio de turno
 * (LE018).
 * </p>
 *
 * <p>
 * Como consecuencia de esa regla, la hora de alimentación de un turno solo
 * puede empezar dentro de la ventana [inicioTurno + 60, inicioTurno + 8h - 120]
 * minutos: comenzar antes dejaría menos de una hora de separación con el cambio
 * de turno que abre la jornada, y terminar después dejaría menos de una hora de
 * separación con el cambio que la cierra.
 * </p>
 */
public final class Turnos {

    public static final int MINUTOS_POR_DIA = 24 * 60;
    public static final int DURACION_TURNO_MIN = 8 * 60;
    public static final int DURACION_ALMUERZO_MIN = 60;
    public static final int SEPARACION_CAMBIO_TURNO_MIN = 60;

    /**
     * Horas de cambio de turno por defecto, configurables por parámetro (LE069).
     */
    private static int[] horasCambioTurno = { 7, 15, 23 };

    private Turnos() {
    }

    public static void configurarCambiosDeTurno(int... horas) {
        if (horas.length != 3) {
            throw new IllegalArgumentException("Se esperan 3 cambios de turno");
        }
        horasCambioTurno = horas.clone();
    }

    public static int[] getHorasCambioTurno() {
        return horasCambioTurno.clone();
    }

    /** Día simulado (1..31) al que pertenece el minuto indicado. */
    public static int dia(int minuto) {
        return minuto / MINUTOS_POR_DIA + 1;
    }

    public static int minutoDelDia(int minuto) {
        return minuto % MINUTOS_POR_DIA;
    }

    /**
     * Convierte día/hora/minuto del formato de los archivos a minutos absolutos.
     */
    public static int aMinutos(int dia, int hora, int minuto) {
        return (dia - 1) * MINUTOS_POR_DIA + hora * 60 + minuto;
    }

    /**
     * Índice del turno (0, 1 o 2) vigente en el minuto indicado. El turno 2
     * (23:00-07:00) cruza la medianoche, por lo que las horas anteriores al primer
     * cambio pertenecen a él.
     */
    public static int indiceTurno(int minuto) {
        int h = minutoDelDia(minuto) / 60;
        if (h >= horasCambioTurno[0] && h < horasCambioTurno[1]) {
            return 0;
        }
        if (h >= horasCambioTurno[1] && h < horasCambioTurno[2]) {
            return 1;
        }
        return 2;
    }

    /** Minuto absoluto en que comenzó el turno vigente en el instante indicado. */
    public static int inicioTurno(int minuto) {
        int dia = dia(minuto);
        int md = minutoDelDia(minuto);
        int t = indiceTurno(minuto);
        if (t == 2 && md < horasCambioTurno[0] * 60) {
            // Turno nocturno iniciado el día anterior a las 23:00.
            return aMinutos(dia - 1, horasCambioTurno[2], 0);
        }
        return aMinutos(dia, horasCambioTurno[t], 0);
    }

    public static int finTurno(int minuto) {
        return inicioTurno(minuto) + DURACION_TURNO_MIN;
    }

    /**
     * Primer minuto en que la unidad puede iniciar su hora de alimentación en ese
     * turno.
     */
    public static int inicioVentanaAlimentacion(int minutoDelTurno) {
        return inicioTurno(minutoDelTurno) + SEPARACION_CAMBIO_TURNO_MIN;
    }

    /**
     * Último minuto en que la unidad puede iniciar su hora de alimentación en ese
     * turno.
     */
    public static int finVentanaAlimentacion(int minutoDelTurno) {
        return finTurno(minutoDelTurno) - SEPARACION_CAMBIO_TURNO_MIN - DURACION_ALMUERZO_MIN;
    }

    /**
     * Verifica que una hora de alimentación que inicia en {@code inicio} respete la
     * separación mínima con ambos cambios de turno de la jornada (LE018).
     */
    public static boolean alimentacionValida(int inicio) {
        return inicio >= inicioVentanaAlimentacion(inicio) && inicio <= finVentanaAlimentacion(inicio);
    }

    /**
     * Representación legible "DDdHHhMMm" coherente con el formato de los archivos
     * de entrada.
     */
    public static String formatear(int minuto) {
        int dia = dia(minuto);
        int md = minutoDelDia(minuto);
        return String.format("%02dd%02dh%02dm", dia, md / 60, md % 60);
    }
}
