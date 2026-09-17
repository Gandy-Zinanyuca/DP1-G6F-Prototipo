package pe.logistica.model;

public record ConfiguracionTabu(long saMinutos, int k, int maxIteraciones, int tenenciaTabu) {
    public ConfiguracionTabu {
        if (saMinutos <= 0 || k <= 0 || maxIteraciones < 0 || tenenciaTabu <= 0)
            throw new IllegalArgumentException("Sa, K y tenencia deben ser positivos; iteraciones >= 0");
        Math.multiplyExact(saMinutos, k);
    }
    public long scMinutos() { return Math.multiplyExact(saMinutos, k); }
    public static ConfiguracionTabu porDefecto() { return new ConfiguracionTabu(30, 4, 100, 7); }
}
