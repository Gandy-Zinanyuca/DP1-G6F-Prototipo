package pe.logistica.model;

import java.time.LocalDate;
import java.util.Objects;

public record Mantenimiento(LocalDate fecha, String codigoVehiculo) {
    public Mantenimiento { Objects.requireNonNull(fecha); TipoVehiculo.desdeCodigo(codigoVehiculo); }
}
