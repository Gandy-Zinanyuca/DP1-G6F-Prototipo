package pe.pucp.paqrap.simulacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Métricas de una corrida de simulación, insumo de la experimentación numérica.
 *
 * <p>La medida principal del escenario "hasta el colapso" es cuánto dura: en tiempo simulado
 * (instante del colapso, días simulados) y en tiempo real de cómputo (tiempo total de la
 * corrida y tiempo acumulado dentro del planificador).</p>
 */
public class ResultadoSimulacion {

    /** Por qué terminó la simulación. */
    public enum Fin {
        /** Un pedido no puede entregarse dentro de su plazo. */
        COLAPSO,
        /** No hay más archivos de ventas y todos los pedidos se entregaron. */
        FIN_DE_DATOS,
        /** Se alcanzó el número máximo de ciclos pedido. */
        LIMITE_DE_CICLOS
    }

    /** Parámetros de la corrida (algoritmo, semilla, Sa, K, ...), en el orden en que se agregan. */
    public final Map<String, String> parametros = new LinkedHashMap<>();

    public Fin fin;
    /** Descripción del colapso: pedido afectado y restricción violada. */
    public String causaColapso = "";
    /** Resumen del diagnóstico del colapso (ver DiagnosticoColapso). */
    public String diagnosticoColapso = "";

    public LocalDateTime instanteInicial;
    public LocalDateTime instanteFinal;
    public int minutoInicial;
    public int minutoFinal;
    public int mesesCargados;

    public int ciclos;
    public int ejecucionesPlanificador;
    /** Tiempo real de toda la corrida, en milisegundos. */
    public long tiempoRealMs;
    /** Suma de los tiempos de ejecución del planificador (Σ Ta), en nanosegundos. */
    public long tiempoPlanificadorNs;
    /** Mayor Ta observado, en nanosegundos. */
    public long taMaximoNs;

    /** Pedidos registrados antes del arranque con plazo aún vigente (incluye el mes anterior). */
    public int pedidosPendientesAlInicio;
    public int pedidosRegistrados;
    public int pedidosEntregados;
    public int pedidosFraccionados;
    public long paquetesEntregados;
    public int rutasDespachadas;
    /** Viajes despachados (una ruta puede tener varios, recargando entre ellos). */
    public int viajesDespachados;
    /** Viajes despachados y unidades en la flota, por tipo de vehículo. */
    public final Map<String, Integer> viajesPorTipo = new LinkedHashMap<>();
    public final Map<String, Integer> unidadesPorTipo = new LinkedHashMap<>();
    /** Ciclos cuyo plan reprogramó algún pedido y máximo de paquetes reprogramados en un ciclo. */
    public int ciclosConPostergacion;
    public int maxPaquetesPostergados;
    public double kmRecorridos;
    /** Verificación de la hora de alimentación sobre lo despachado; null si no se evaluó. */
    public AuditoriaAlimentacion auditoriaAlimentacion;
    public double costoSoles;

    public double diasSimulados() {
        return (minutoFinal - minutoInicial) / 1440.0;
    }

    /** Promedio de viajes despachados por unidad y por día simulado del tipo indicado. */
    public double viajesPorUnidadDia(String tipo) {
        int unidades = unidadesPorTipo.getOrDefault(tipo, 0);
        double dias = diasSimulados();
        return unidades == 0 || dias <= 0 ? 0 : viajesPorTipo.getOrDefault(tipo, 0) / (unidades * dias);
    }

    public double taPromedioMs() {
        return ejecucionesPlanificador == 0 ? 0 : tiempoPlanificadorNs / 1e6 / ejecucionesPlanificador;
    }

    private Map<String, String> metricas() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("fin", fin.name());
        m.put("instante_inicial", instanteInicial.toString());
        m.put("instante_final", instanteFinal.toString());
        m.put("dias_simulados", fmt("%.3f", diasSimulados()));
        m.put("meses_cargados", String.valueOf(mesesCargados));
        m.put("ciclos", String.valueOf(ciclos));
        m.put("ejecuciones_planificador", String.valueOf(ejecucionesPlanificador));
        m.put("tiempo_real_s", fmt("%.3f", tiempoRealMs / 1000.0));
        m.put("tiempo_planificador_s", fmt("%.3f", tiempoPlanificadorNs / 1e9));
        m.put("ta_promedio_ms", fmt("%.3f", taPromedioMs()));
        m.put("ta_maximo_ms", fmt("%.3f", taMaximoNs / 1e6));
        m.put("pendientes_al_inicio", String.valueOf(pedidosPendientesAlInicio));
        m.put("pedidos_registrados", String.valueOf(pedidosRegistrados));
        m.put("pedidos_entregados", String.valueOf(pedidosEntregados));
        m.put("pedidos_fraccionados", String.valueOf(pedidosFraccionados));
        m.put("paquetes_entregados", String.valueOf(paquetesEntregados));
        m.put("rutas_despachadas", String.valueOf(rutasDespachadas));
        m.put("viajes_despachados", String.valueOf(viajesDespachados));
        for (String tipo : unidadesPorTipo.keySet()) {
            m.put("viajes_unidad_dia_" + tipo.toLowerCase(), fmt("%.2f", viajesPorUnidadDia(tipo)));
        }
        m.put("ciclos_con_reprogramacion", String.valueOf(ciclosConPostergacion));
        m.put("max_paquetes_reprogramados", String.valueOf(maxPaquetesPostergados));
        m.put("km", fmt("%.0f", kmRecorridos));
        m.put("costo_soles", fmt("%.2f", costoSoles));
        if (auditoriaAlimentacion != null) {
            m.put("turnos_unidad_evaluados", String.valueOf(auditoriaAlimentacion.turnosEvaluados));
            m.put("turnos_sin_alimentacion", String.valueOf(auditoriaAlimentacion.incumplidos));
        }
        m.put("causa_colapso", causaColapso);
        m.put("diagnostico_colapso", diagnosticoColapso);
        return m;
    }

    /** Agrega una fila al CSV de resumen, escribiendo la cabecera si el archivo es nuevo. */
    public void agregarACsv(Path archivo) throws IOException {
        Map<String, String> fila = new LinkedHashMap<>();
        fila.put("fecha_ejecucion", LocalDateTime.now().withNano(0).toString());
        fila.putAll(parametros);
        fila.putAll(metricas());

        StringBuilder sb = new StringBuilder();
        boolean nuevo = !Files.exists(archivo) || Files.size(archivo) == 0;
        if (nuevo) {
            sb.append(String.join(",", fila.keySet())).append('\n');
        }
        boolean primero = true;
        for (String v : fila.values()) {
            if (!primero) {
                sb.append(',');
            }
            sb.append(csv(v));
            primero = false;
        }
        sb.append('\n');
        if (archivo.getParent() != null) {
            Files.createDirectories(archivo.getParent());
        }
        Files.write(archivo, sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static String csv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private static String fmt(String formato, double valor) {
        return String.format(Locale.ROOT, formato, valor);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(" Fin de la simulación : %s%n", fin));
        if (fin == Fin.COLAPSO) {
            sb.append(String.format(" Causa del colapso    : %s%n", causaColapso));
            if (!diagnosticoColapso.isEmpty()) {
                sb.append(String.format(" Diagnóstico          : %s%n", diagnosticoColapso));
            }
        }
        sb.append(String.format(" Periodo simulado     : %s -> %s (%.2f días, %d meses cargados)%n",
                instanteInicial, instanteFinal, diasSimulados(), mesesCargados));
        sb.append(String.format(" Tiempo real          : %.2f s (planificador: %.2f s en %d ejecuciones;"
                        + " Ta prom. %.1f ms, máx. %.1f ms)%n",
                tiempoRealMs / 1000.0, tiempoPlanificadorNs / 1e9, ejecucionesPlanificador,
                taPromedioMs(), taMaximoNs / 1e6));
        sb.append(String.format(" Ciclos               : %d%n", ciclos));
        sb.append(String.format(" Pedidos              : %d registrados (%d pendientes al inicio) · %d entregados"
                        + " · %d fraccionados · %d paquetes%n",
                pedidosRegistrados, pedidosPendientesAlInicio, pedidosEntregados, pedidosFraccionados,
                paquetesEntregados));
        sb.append(String.format(" Rutas despachadas    : %d rutas · %d viajes · %.0f km · S/ %.2f%n",
                rutasDespachadas, viajesDespachados, kmRecorridos, costoSoles));
        StringBuilder porTipo = new StringBuilder();
        for (String tipo : unidadesPorTipo.keySet()) {
            porTipo.append(porTipo.length() == 0 ? "" : " · ")
                    .append(String.format("%s %.2f", tipo.toLowerCase(), viajesPorUnidadDia(tipo)));
        }
        sb.append(String.format(" Viajes/unidad/día    : %s%n", porTipo));
        sb.append(String.format(" Reprogramación       : %d ciclos reprogramaron pedidos (máx. %d paquetes en un ciclo)%n",
                ciclosConPostergacion, maxPaquetesPostergados));
        if (auditoriaAlimentacion != null) {
            sb.append(auditoriaAlimentacion);
        }
        return sb.toString();
    }
}
