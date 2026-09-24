package pe.pucp.paqrap.alns.estricto;

public record ConfiguracionALNS(int maxIteraciones, int sinMejoraMax, int destruccionMax, int segmento, double reaccion,
        double aceptacionInicial, long presupuestoMs, long semilla) {
    public ConfiguracionALNS {
        if (maxIteraciones < 0 || sinMejoraMax <= 0 || destruccionMax <= 0 || segmento <= 0
                || !Double.isFinite(reaccion) || reaccion < 0 || reaccion > 1 || !Double.isFinite(aceptacionInicial)
                || aceptacionInicial <= 0 || presupuestoMs < 0)
            throw new IllegalArgumentException("Configuracion ALNS invalida");
    }

    public static ConfiguracionALNS porDefecto() {
        return new ConfiguracionALNS(100, 40, 8, 10, .7, .05, 0, 20262);
    }
}
