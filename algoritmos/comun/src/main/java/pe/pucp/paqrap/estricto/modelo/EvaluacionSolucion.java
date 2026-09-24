package pe.pucp.paqrap.estricto.modelo;

import java.util.*;

public record EvaluacionSolucion(List<ResultadoRuta> rutas, List<String> errores, double objetivo,
        int paquetesPendientes) {
    public EvaluacionSolucion {
        rutas = List.copyOf(rutas);
        errores = List.copyOf(errores);
    }

    public boolean factible() {
        return errores.isEmpty();
    }

    public boolean completa() {
        return factible() && paquetesPendientes == 0;
    }

    public double costo() {
        return rutas.stream().mapToDouble(ResultadoRuta::costo).sum();
    }
}
