package pe.pucp.paqrap.datos;

import pe.pucp.paqrap.mapa.MapaUrbano;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Mantenimiento;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.TipoVehiculo;
import pe.pucp.paqrap.modelo.Vehiculo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos estáticos de una ejecución: mapa, almacenes, flota, pedidos del mes y mantenimientos.
 *
 * <p>El reloj de la simulación cuenta minutos desde el día 1, 00:00, del mes inicial
 * ({@link #getFechaInicio()}); el "día simulado" 1 es esa fecha, y los días siguientes pueden
 * pertenecer a meses posteriores cuando la simulación los encadena.</p>
 *
 * <p>Es el insumo común de los dos algoritmos del componente planificador; ninguno de ellos
 * modifica la instancia. El estado que cambia durante la simulación (posición de las unidades,
 * stock de los almacenes, pedidos pendientes) viaja en el contexto de cada ciclo.</p>
 */
public class Instancia {

    /** Composición por defecto de la flota, derivada de los códigos del archivo de mantenimiento. */
    public static final int AUTOS_POR_DEFECTO = 10;
    public static final int MOTOS_POR_DEFECTO = 15;
    public static final int BICICLETAS_POR_DEFECTO = 12;

    private final MapaUrbano mapa;
    private final List<Almacen> almacenes;
    private final Map<String, Almacen> almacenesPorId = new LinkedHashMap<>();
    private final List<Vehiculo> flota;
    private final Map<String, Vehiculo> flotaPorCodigo = new LinkedHashMap<>();
    private final List<Pedido> pedidos;
    private final List<Mantenimiento> mantenimientos;

    private final int anioSimulado;
    private final int mesSimulado;
    private final LocalDate fechaInicio;

    public Instancia(MapaUrbano mapa, List<Almacen> almacenes, List<Vehiculo> flota,
                     List<Pedido> pedidos, List<Mantenimiento> mantenimientos,
                     int anioSimulado, int mesSimulado) {
        this.mapa = mapa;
        this.almacenes = almacenes;
        this.flota = flota;
        this.pedidos = pedidos;
        this.mantenimientos = mantenimientos;
        this.anioSimulado = anioSimulado;
        this.mesSimulado = mesSimulado;
        this.fechaInicio = LocalDate.of(anioSimulado, mesSimulado, 1);
        for (Almacen a : almacenes) {
            almacenesPorId.put(a.getId(), a);
        }
        for (Vehiculo v : flota) {
            flotaPorCodigo.put(v.getCodigo(), v);
        }
    }

    /**
     * Construye la instancia estándar del curso a partir de los tres archivos de entrada.
     *
     * @param archivoVentas         archivo mensual de ventas (ventas.AAAAMM.txt)
     * @param archivoBloqueos       archivo mensual de bloqueos (bloqueo.AAMM.txt), opcional
     * @param archivoMantenimiento  archivo de mantenimiento preventivo, opcional
     * @param capacidadIntermedios  capacidad de cada almacén intermedio (LE031)
     * @param autos, motos, bicis   composición de la flota (LE067, LE068)
     */
    public static Instancia construir(Path archivoVentas, Path archivoBloqueos,
                                      Path archivoMantenimiento, int anio, int mes,
                                      int capacidadIntermedios,
                                      int autos, int motos, int bicis) throws IOException {

        CargadorVentas.Resultado rv = CargadorVentas.cargar(archivoVentas);

        List<pe.pucp.paqrap.modelo.Bloqueo> bloqueos = new ArrayList<>();
        if (archivoBloqueos != null) {
            bloqueos = CargadorBloqueos.cargar(archivoBloqueos).bloqueos;
        }
        List<Mantenimiento> mantenimientos = new ArrayList<>();
        if (archivoMantenimiento != null) {
            mantenimientos = CargadorMantenimiento.cargar(archivoMantenimiento);
        }

        List<Almacen> almacenes = new ArrayList<>();
        Collections.addAll(almacenes, Almacen.configuracionEstandar(capacidadIntermedios));

        List<Vehiculo> flota = construirFlota(almacenes.get(0), autos, motos, bicis);

        return new Instancia(new MapaUrbano(bloqueos), almacenes, flota, rv.pedidos,
                mantenimientos, anio, mes);
    }

    /**
     * Genera la flota con la nomenclatura del archivo de mantenimiento (TA/TM/TB + correlativo).
     * Todas las unidades inician la jornada en el almacén central (LE020).
     */
    public static List<Vehiculo> construirFlota(Almacen almacenCentral, int autos, int motos, int bicis) {
        List<Vehiculo> flota = new ArrayList<>(autos + motos + bicis);
        for (int i = 1; i <= autos; i++) {
            flota.add(new Vehiculo(String.format("TA%02d", i), TipoVehiculo.AUTO,
                    almacenCentral.getUbicacion()));
        }
        for (int i = 1; i <= motos; i++) {
            flota.add(new Vehiculo(String.format("TM%02d", i), TipoVehiculo.MOTO,
                    almacenCentral.getUbicacion()));
        }
        for (int i = 1; i <= bicis; i++) {
            flota.add(new Vehiculo(String.format("TB%02d", i), TipoVehiculo.BICICLETA,
                    almacenCentral.getUbicacion()));
        }
        return flota;
    }

    public MapaUrbano getMapa() {
        return mapa;
    }

    public List<Almacen> getAlmacenes() {
        return almacenes;
    }

    public Almacen getAlmacen(String id) {
        return almacenesPorId.get(id);
    }

    public Almacen getAlmacenCentral() {
        for (Almacen a : almacenes) {
            if (a.esCentral()) {
                return a;
            }
        }
        throw new IllegalStateException("La instancia no define almacén central");
    }

    public List<Vehiculo> getFlota() {
        return flota;
    }

    public Vehiculo getVehiculo(String codigo) {
        return flotaPorCodigo.get(codigo);
    }

    public List<Pedido> getPedidos() {
        return pedidos;
    }

    public List<Mantenimiento> getMantenimientos() {
        return mantenimientos;
    }

    /** Códigos de unidad que están en mantenimiento preventivo el día simulado indicado. */
    public List<String> unidadesEnMantenimiento(int diaSimulado) {
        List<String> codigos = new ArrayList<>();
        for (Mantenimiento m : mantenimientos) {
            if (diaSimulado(m) == diaSimulado) {
                codigos.add(m.getCodigoUnidad());
            }
        }
        return codigos;
    }

    /** Día simulado (1 = primer día del mes inicial) en que cae el mantenimiento. */
    public int diaSimulado(Mantenimiento m) {
        return (int) ChronoUnit.DAYS.between(fechaInicio, m.getFecha()) + 1;
    }

    /** Primer día del mes inicial: el minuto 0 de la simulación. */
    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public int getAnioSimulado() {
        return anioSimulado;
    }

    public int getMesSimulado() {
        return mesSimulado;
    }

    @Override
    public String toString() {
        return "Instancia[" + pedidos.size() + " pedidos, " + flota.size() + " unidades, "
                + mapa.getBloqueos().size() + " bloqueos, " + almacenes.size() + " almacenes]";
    }
}
