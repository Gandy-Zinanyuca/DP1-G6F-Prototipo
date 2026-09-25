package pe.pucp.paqrap.tabu;

/** Presupuesto por iteraciones/candidatos; sin Sa, Sc ni K. */
public record ConfiguracionTabu(int maxIteraciones, int tenenciaTabu, int sinMejoraMax, int candidatosPorIteracion,
        long presupuestoMs, long semilla) {
    public ConfiguracionTabu {
        if (maxIteraciones < 0 || tenenciaTabu <= 0 || sinMejoraMax <= 0 || candidatosPorIteracion < 2
                || presupuestoMs < 0)
            throw new IllegalArgumentException("Configuracion Tabu invalida");
    }

    public static ConfiguracionTabu porDefecto() {
        return new ConfiguracionTabu(100, 7, 40, 1000, 0, 20262);
    }
}
