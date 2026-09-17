package pe.logistica.model;

import java.util.ArrayList;
import java.util.List;

/** Excel publicado el 09/09/2026, PR_Proyecto!D19 y Flota!B3:D5. */
public final class ReferenciaProyecto {
    private ReferenciaProyecto() { }
    public static final Nodo CENTRAL = new Nodo(27, 14);
    public static final Nodo NOROESTE = new Nodo(12, 38);
    public static final Nodo ESTE = new Nodo(57, 27);
    public static List<Vehiculo> flota(Nodo origen) {
        var flota = new ArrayList<Vehiculo>();
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            int cantidad = switch (tipo) { case TA -> 10; case TM -> 15; case TB -> 12; };
            for (int n = 1; n <= cantidad; n++) flota.add(new Vehiculo(String.format(java.util.Locale.ROOT, "%s%02d", tipo, n), origen));
        }
        return List.copyOf(flota);
    }
}
