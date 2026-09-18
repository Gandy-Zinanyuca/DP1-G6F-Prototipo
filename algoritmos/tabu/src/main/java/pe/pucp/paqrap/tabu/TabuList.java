package pe.pucp.paqrap.tabu;

import java.util.HashMap;
import java.util.Map;

public final class TabuList {
    private final Map<TabuMove.Clave, Long> expiraciones = new HashMap<>();

    public boolean esTabu(TabuMove movimiento, int iteracion) {
        return expiraciones.getOrDefault(movimiento.claveConsulta(), -1L) >= iteracion;
    }

    /** Registrado en i con tenencia t: prohibido en i+1, ..., i+t. */
    public void registrar(TabuMove movimiento, int iteracion, int tenencia) {
        if (tenencia <= 0)
            throw new IllegalArgumentException("Tenencia debe ser positiva");
        expiraciones.merge(movimiento.claveInversa(), (long) iteracion + tenencia, Math::max);
    }

    public void depurar(int iteracion) {
        expiraciones.values().removeIf(fin -> fin < iteracion);
    }
}
