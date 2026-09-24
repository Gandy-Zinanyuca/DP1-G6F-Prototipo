package pe.pucp.paqrap.estricto.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

public record Vehiculo(String codigo, TipoVehiculo tipo, Nodo ubicacionInicial, boolean disponible,
        LocalDateTime disponibleDesde) {
    public Vehiculo {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(ubicacionInicial);
        Objects.requireNonNull(disponibleDesde);
        if (TipoVehiculo.desdeCodigo(codigo) != tipo)
            throw new IllegalArgumentException("El codigo no coincide con el tipo: " + codigo);
    }

    public Vehiculo(String codigo, Nodo origen) {
        this(codigo, TipoVehiculo.desdeCodigo(codigo), origen, true, LocalDateTime.MIN);
    }
}
