package pe.pucp.paqrap.estricto.modelo;

public enum TipoVehiculo {
    TA("Auto", 24, 40, 8), TM("Moto", 8, 25, 6), TB("Bicicleta", 4, 12, 3);

    private final String descripcion;
    private final int capacidad, velocidadKmh, costoPorKm;

    TipoVehiculo(String descripcion, int capacidad, int velocidadKmh, int costoPorKm) {
        this.descripcion = descripcion;
        this.capacidad = capacidad;
        this.velocidadKmh = velocidadKmh;
        this.costoPorKm = costoPorKm;
    }

    public String descripcion() {
        return descripcion;
    }

    public int capacidad() {
        return capacidad;
    }

    public int velocidadKmh() {
        return velocidadKmh;
    }

    public int costoPorKm() {
        return costoPorKm;
    }

    public static TipoVehiculo desdeCodigo(String codigo) {
        if (codigo == null || !codigo.matches("T[AMB][0-9]{2}"))
            throw new IllegalArgumentException("Codigo de vehiculo invalido (TTNN): " + codigo);
        return valueOf(codigo.substring(0, 2));
    }
}
