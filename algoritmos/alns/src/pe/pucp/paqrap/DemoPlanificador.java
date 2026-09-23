package pe.pucp.paqrap;

import pe.pucp.paqrap.alns.ParametrosALNS;
import pe.pucp.paqrap.datos.Instancia;
import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.planificador.PlanificadorALNS;
import pe.pucp.paqrap.simulacion.FuentesDeDatos;
import pe.pucp.paqrap.simulacion.ParametrosSimulacion;
import pe.pucp.paqrap.simulacion.ResultadoSimulacion;
import pe.pucp.paqrap.simulacion.Simulador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Programa de ejecución del componente planificador sobre los archivos del curso.
 *
 * <p>Construye la instancia con el mes del archivo de ventas y la simula con {@link Simulador}:
 * reloj de Sa minutos, despacho progresivo de rutas, entregas registradas al llegar y meses
 * encadenados. Dos modos:</p>
 * <ul>
 *   <li><b>Por ciclos</b> (por defecto): simula {@code --ciclos N} ciclos de planificación con
 *       la configuración de operación diaria.</li>
 *   <li><b>Hasta el colapso</b> ({@code --colapso}): sin límite de ciclos, encadenando los meses
 *       siguientes que existan en las mismas carpetas, hasta que un pedido no pueda entregarse a
 *       tiempo. Usa la configuración de colapso de ALNS.</li>
 * </ul>
 *
 * <h2>Uso</h2>
 * <pre>
 *   java -cp out pe.pucp.paqrap.DemoPlanificador &lt;ventas.txt&gt; [bloqueos.txt] [mantenimiento.txt]
 *        [--colapso] [--dia N] [--hora N] [--ciclos N] [--sa MIN] [--k N] [--iteraciones N]
 *        [--semilla N] [--anio AAAA] [--mes MM] [--detalle] [--traza]
 *        [--csv-ciclos archivo.csv] [--csv-resumen archivo.csv]
 *        [--sin-recargas] [--max-viajes N] [--sin-reprogramacion] [--holgura-reprogramacion MIN]
 * </pre>
 *
 * <p>El año y el mes se deducen del nombre del archivo de ventas ({@code ventas.AAAAMM.txt}); con
 * {@code --anio} y {@code --mes} se pueden forzar. Los meses siguientes se buscan en la carpeta
 * del archivo de ventas ({@code ventas.AAAAMM.txt}) y en la del archivo de bloqueos
 * ({@code bloqueo.AAMM.txt}).</p>
 */
public final class DemoPlanificador {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java pe.pucp.paqrap.DemoPlanificador <ventas.txt> "
                    + "[bloqueos.txt] [mantenimiento.txt] [--colapso] [--dia N] [--hora N] [--ciclos N] "
                    + "[--sa MIN] [--k N] [--iteraciones N] [--semilla N] [--anio AAAA] [--mes MM] "
                    + "[--detalle] [--traza] [--csv-ciclos archivo] [--csv-resumen archivo] "
                    + "[--sin-recargas] [--max-viajes N] [--sin-reprogramacion] [--holgura-reprogramacion MIN]");
            return;
        }

        Path ventas = Paths.get(args[0]);
        Path bloqueos = null;
        Path mantenimiento = null;
        ParametrosSimulacion parSim = new ParametrosSimulacion();
        parSim.horaInicial = 7;
        int ciclos = 8;
        boolean colapso = false;
        Long saMinutos = null;
        Integer k = null;
        Integer iteraciones = null;
        long semilla = 20262L;
        Integer anio = null;
        Integer mes = null;
        boolean traza = false;
        ParametrosPlanificador parPlan = new ParametrosPlanificador();

        List<String> posicionales = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--colapso":
                    colapso = true;
                    break;
                case "--dia":
                    parSim.diaInicial = Integer.parseInt(args[++i]);
                    break;
                case "--hora":
                    parSim.horaInicial = Integer.parseInt(args[++i]);
                    break;
                case "--ciclos":
                    ciclos = Integer.parseInt(args[++i]);
                    break;
                case "--anio":
                    anio = Integer.parseInt(args[++i]);
                    break;
                case "--mes":
                    mes = Integer.parseInt(args[++i]);
                    break;
                case "--semilla":
                    semilla = Long.parseLong(args[++i]);
                    break;
                case "--traza":
                    traza = true;
                    break;
                case "--detalle":
                    parSim.detalle = true;
                    break;
                case "--csv-ciclos":
                    parSim.csvCiclos = Paths.get(args[++i]);
                    break;
                case "--sin-recargas":
                    parPlan.permitirRecargas = false;
                    break;
                case "--max-viajes":
                    parPlan.maxViajesPorRuta = Integer.parseInt(args[++i]);
                    break;
                case "--sin-reprogramacion":
                    parPlan.permitirPostergacion = false;
                    break;
                case "--holgura-reprogramacion":
                    parPlan.holguraMinimaPostergacionMin = Integer.parseInt(args[++i]);
                    break;
                case "--csv-resumen":
                    parSim.csvResumen = Paths.get(args[++i]);
                    break;
                case "--sa":
                    saMinutos = Long.parseLong(args[++i]);
                    break;
                case "--k":
                    k = Integer.parseInt(args[++i]);
                    break;
                case "--iteraciones":
                    iteraciones = Integer.parseInt(args[++i]);
                    break;
                default:
                    posicionales.add(args[i]);
            }
        }
        if (!posicionales.isEmpty()) {
            bloqueos = Paths.get(posicionales.get(0));
        }
        if (posicionales.size() > 1) {
            mantenimiento = Paths.get(posicionales.get(1));
        }
        Path carpetaBloqueos = bloqueos == null ? null : bloqueos.toAbsolutePath().getParent();
        if (bloqueos != null && !Files.exists(bloqueos)) {
            bloqueos = null;
        }
        if (mantenimiento != null && !Files.exists(mantenimiento)) {
            mantenimiento = null;
        }

        // Año y mes del archivo de ventas (ventas.AAAAMM.txt), salvo que se indiquen explícitamente.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})(\\d{2})")
                .matcher(ventas.getFileName().toString());
        if (m.find()) {
            if (anio == null) {
                anio = Integer.parseInt(m.group(1));
            }
            if (mes == null) {
                mes = Integer.parseInt(m.group(2));
            }
        }
        if (anio == null || mes == null) {
            System.out.println("No se pudo deducir el año y mes del archivo de ventas; use --anio y --mes.");
            return;
        }

        // ---------------------------------------------------------------- carga
        Instancia instancia = Instancia.construir(
                ventas, bloqueos, mantenimiento,
                anio, mes,
                Almacen.CAPACIDAD_INTERMEDIO_POR_DEFECTO,
                Instancia.AUTOS_POR_DEFECTO,
                Instancia.MOTOS_POR_DEFECTO,
                Instancia.BICICLETAS_POR_DEFECTO);

        PlanificadorALNS planificador = colapso
                ? PlanificadorALNS.paraColapso()
                : PlanificadorALNS.paraOperacionDiaria();
        ParametrosALNS parAlns = planificador.getParametros();
        parAlns.traza = traza;
        if (saMinutos != null) {
            parAlns.saMinutos = saMinutos;
        }
        if (k != null) {
            parAlns.k = k;
        }
        if (iteraciones != null) {
            parAlns.maxIteraciones = iteraciones;
        }
        planificador.reiniciarMotor(semilla);
        parSim.saMinutos = (int) parAlns.saMinutos;
        parSim.scMinutos = (int) parAlns.scMinutos();
        parSim.maxCiclos = colapso ? 0 : ciclos;

        System.out.println("=====================================================================");
        System.out.println(" PaqRap · Componente planificador · ALNS"
                + (colapso ? " · simulación hasta el colapso" : ""));
        System.out.println("=====================================================================");
        System.out.printf("Mes inicial: %04d-%02d · %s%n", anio, mes, instancia);
        System.out.println("Almacenes: " + instancia.getAlmacenes());
        System.out.printf("Recargas en ruta: %s · reprogramación: %s%n",
                parPlan.permitirRecargas ? "sí (hasta " + parPlan.maxViajesPorRuta + " viajes por ruta)" : "no",
                parPlan.permitirPostergacion ? "sí (holgura > " + parPlan.holguraMinimaPostergacionMin + " min)" : "no");
        System.out.printf("Sa=%d min · K=%d · Sc=%d min · maxIteraciones=%d · proporciónDestrucción=%.2f"
                        + " · semilla=%d · %s%n%n",
                parSim.saMinutos, parAlns.k, parSim.scMinutos, parAlns.maxIteraciones,
                parAlns.proporcionDestruccion, semilla,
                colapso ? "sin límite de ciclos" : "ciclos=" + ciclos);

        YearMonth mesInicial = YearMonth.of(anio, mes);
        FuentesDeDatos fuentes = new FuentesDeDatos(ventas.toAbsolutePath().getParent(), carpetaBloqueos);
        Simulador simulador = new Simulador(instancia, mesInicial, fuentes, planificador, parSim,
                parPlan, System.out);

        ResultadoSimulacion r = simulador.getResultado();
        r.parametros.put("algoritmo", planificador.nombre());
        r.parametros.put("escenario", colapso ? "colapso" : "ciclos");
        r.parametros.put("mes_inicial", mesInicial.toString());
        r.parametros.put("semilla", String.valueOf(semilla));
        r.parametros.put("sa_min", String.valueOf(parSim.saMinutos));
        r.parametros.put("k", String.valueOf(parAlns.k));
        r.parametros.put("max_iteraciones", String.valueOf(parAlns.maxIteraciones));
        r.parametros.put("proporcion_destruccion", String.valueOf(parAlns.proporcionDestruccion));
        r.parametros.put("recargas_en_ruta", parPlan.permitirRecargas ? "max" + parPlan.maxViajesPorRuta + "viajes" : "no");
        r.parametros.put("reprogramacion", parPlan.permitirPostergacion
                ? "holgura>" + parPlan.holguraMinimaPostergacionMin + "min" : "no");

        simulador.ejecutar();

        System.out.println("=====================================================================");
        if (r.fin == ResultadoSimulacion.Fin.COLAPSO) {
            System.out.println(" COLAPSO LOGÍSTICO en " + simulador.formatear(r.minutoFinal));
        }
        System.out.print(r);
        System.out.println("=====================================================================");
    }

    private DemoPlanificador() {
    }
}
