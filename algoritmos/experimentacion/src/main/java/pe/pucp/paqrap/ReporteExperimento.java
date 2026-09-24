package pe.pucp.paqrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.*;

/** Esquema comun; valores no aplicables se exportan como celdas vacias. */
final class ReporteExperimento {
    static final String CABECERA = "escenario,nivel_carga,factor_carga,instancia,estado_sha256,semilla,repeticion,algoritmo,instante,estado_resultado,colapso,instante_colapso,causa_colapso,parada,Ta_ms,holgura_promedio_min,holgura_minima_min,Ta_primera_completa_ms,iteracion_primera_completa,iteraciones,iteracion_mejor,candidatos,inserciones_iniciales,pedidos_totales,pedidos_completos,pedidos_pendientes,paquetes_pendientes,cobertura_pedidos,cobertura_paquetes,distancia_km,tiempo_rutas_min,vehiculos,utilizacion_capacidad,costo_soles,objetivo,factible,completa,max_iteraciones,presupuesto_ms\n";

    static String hash(String texto) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    static String celda(Object v) {
        if (v == null)
            return "";
        return "\"" + v.toString().replace("\"", "\"\"") + "\"";
    }

    static String fila(EntradaExperimento e, ResultadoPlanificacion r, long seed, int rep, int iter, long presupuesto)
            throws Exception {
        var m = r.metricas();
        Object[] valores = { e.escenario, e.nivel, e.factor, e.instancia, hash(e.estado.toString()), seed, rep,
                r.algoritmo(), e.estado.instante(), m.estadoResultado(), m.colapso(),
                m.colapso() ? e.estado.instante() : null, m.colapso() ? "SIN_SOLUCION_COMPLETA" : null, m.parada(),
                m.taMs(), m.holguraPromedioMin(), m.holguraMinimaMin(), m.taPrimeraCompletaMs(),
                m.iteracionPrimeraCompleta(), m.iteraciones(), m.iteracionMejor(), m.candidatosEvaluados(),
                m.insercionesIniciales(), m.pedidosTotales(), m.pedidosCompletos(),
                m.pedidosTotales() - m.pedidosCompletos(), m.paquetesPendientes(), m.cumplimientoPedidos(),
                m.cumplimientoPaquetes(), m.distanciaKm(), m.tiempoRutasMinutos(), m.vehiculosUsados(),
                m.utilizacionCapacidad(), m.costoOperacion(), m.objetivo(), r.evaluacion().factible(),
                r.evaluacion().completa(), iter, presupuesto };
        return String.join(",", Arrays.stream(valores).map(ReporteExperimento::celda).toList()) + "\n";
    }

    static void auditar(EntradaExperimento e, ResultadoPlanificacion r) {
        var a = new EvaluadorFactibilidad(e.estado, ParametrosOperacion.porDefecto()).evaluar(r.solucion());
        if (!a.factible() || Math.abs(a.objetivo() - r.metricas().objetivo()) > 1e-9)
            throw new AssertionError("Resultado invalido; no debe tratarse como colapso experimental");
    }

    static String numero(Double n) {
        return n == null ? "N/A" : String.format(Locale.ROOT, "%.3f", n);
    }

    static void consola(EntradaExperimento e, ResultadoPlanificacion r, long seed, int rep) {
        var m = r.metricas();
        System.out.printf(Locale.ROOT,
                "%n=== %s | %s | carga %s | %s ===%n" + "Instancia: %s | semilla: %d | repeticion: %d | instante: %s%n"
                        + "Principales: Ta = %.3f ms | holgura promedio = %s min%n"
                        + "Complementarias: holgura minima = %s min | distancia = %.3f km | tiempo rutas = %.3f min%n"
                        + "Flota: %d vehiculos | utilizacion = %.2f%% | costo = S/ %.2f%n"
                        + "Cobertura planificada: pedidos %d/%d (%.2f%%) | paquetes %.2f%% | pendientes %d%n"
                        + "Busqueda: %d iteraciones | mejor en %d | candidatos %d | parada %s%n"
                        + "Primera completa: %s ms | iteracion %s | rutas factibles: %s%n",
                r.algoritmo(), e.escenario, e.nivel, m.estadoResultado(), e.instancia, seed, rep, e.estado.instante(),
                m.taMs(), numero(m.holguraPromedioMin()), numero(m.holguraMinimaMin()), m.distanciaKm(),
                m.tiempoRutasMinutos(), m.vehiculosUsados(), 100 * m.utilizacionCapacidad(), m.costoOperacion(),
                m.pedidosCompletos(), m.pedidosTotales(), 100 * m.cumplimientoPedidos(), 100 * m.cumplimientoPaquetes(),
                m.paquetesPendientes(), m.iteraciones(), m.iteracionMejor(), m.candidatosEvaluados(), m.parada(),
                numero(m.taPrimeraCompletaMs()), Objects.toString(m.iteracionPrimeraCompleta(), "N/A"),
                r.evaluacion().factible());
        if (m.colapso())
            System.out.println("Colapso: SIN_SOLUCION_COMPLETA en el instante de planificacion; holgura no aplicable.");
    }

    static void metadatos(Path salida, EntradaExperimento e, int iter, long presupuesto, String semillas)
            throws Exception {
        var s = new StringBuilder("# Ejecucion experimental\n\nEsquema: 2 (holgura y colapso).\n\n");
        s.append("Escenario: ").append(e.escenario).append("; nivel: ").append(e.nivel).append("; factor: ")
                .append(e.factor).append("; instancia: ").append(e.instancia).append("; semillas: ").append(semillas)
                .append("\n\n");
        s.append("Iteraciones: ").append(iter).append("; presupuesto ms: ").append(presupuesto)
                .append("; max pedidos: ").append(e.maxPedidos).append("; horizonte horas: ").append(e.horizonte)
                .append("\n\n");
        s.append("Pedidos leidos: ").append(e.carga.pedidosLeidos()).append("; futuros excluidos: ")
                .append(e.carga.futurosExcluidos()).append("; fuera horizonte: ").append(e.carga.fueraHorizonte())
                .append("; fuera limite: ").append(e.carga.fueraLimite()).append("\n\n");
        for (int i = 0; i < 2; i++)
            s.append("Archivo: ").append(Path.of(e.args[i]).toAbsolutePath()).append(" SHA256: ")
                    .append(HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(e.args[i])))))
                    .append("\n\n");
        s.append("Reglas experimentales v3: bloqueos incluidos; averias y mantenimiento excluidos.\n\n")
                .append("Almacenes: ").append(e.estado.almacenes()).append("\n\n");
        s.append("Estado SHA256: ").append(hash(e.estado.toString())).append("\n\nParametros: ")
                .append(ParametrosOperacion.porDefecto())
                .append("\n\nTS: tenencia 7, candidatos/iteracion 400. ALNS: destruccion 4, segmento 5, reaccion 0.7, temperatura 0.05. Ambos: estancamiento=max(1,iteraciones).\n")
                .append("\nJava: ").append(System.getProperty("java.version")).append("; SO: ")
                .append(System.getProperty("os.name")).append("; procesadores: ")
                .append(Runtime.getRuntime().availableProcessors()).append("\n\n")
                .append("Ta incluye inicializacion, solucion inicial, busqueda y metricas; excluye carga, auditoria, consola y CSV.\n")
                .append("Limite temporal cooperativo: puede excederse. Para reproducibilidad usar presupuesto 0; Ta siempre varia.\n")
                .append("Objetivo: pendientes + 0.5 - atan(holgura de pedidos completos / 60)/pi. Sin pedidos completos: calidad 0.5. Menor es mejor. Costo complementario.\n")
                .append("Holgura: deadline - max(finServicio de partes), promedio por pedido. Solo se exporta para demanda completa no vacia.\n")
                .append("Colapso: demanda no vacia sin plan completo al terminar. No demuestra imposibilidad matematica.\n")
                .append("Instantanea: instante de colapso = instante de planificacion; no son dias de simulacion.\n")
                .append("Escenarios son etiquetas; factor-carga escala cantidades con techo. E1: demanda base con bloqueos. E2: carga creciente con los mismos bloqueos. E3: variantes documentadas de bloqueos. Sin averias ni mantenimiento.\n")
                .append("No se reconstruyen entregas previas. Pedidos vencidos del archivo pueden causar colapso inmediato.\n")
                .append("Candidatos TS/ALNS e igual numero de iteraciones no representan igual trabajo.\n");
        Files.writeString(salida.resolve("README.md"), s, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(salida.resolve("estado.txt"), e.estado.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    static void detalle(Path salida, ResultadoPlanificacion r, long seed, int repeticion) throws Exception {
        var s = new StringBuilder("# " + r.algoritmo() + " / semilla " + seed + " / repeticion " + repeticion + "\n\n");
        s.append(r.metricas()).append("\n\n");
        for (var ruta : r.evaluacion().rutas())
            if (!ruta.ruta().partes().isEmpty()) {
                s.append("## ").append(ruta.ruta().vehiculo()).append("\n\n").append("Salida: ").append(ruta.salida())
                        .append("; retorno: ").append(ruta.fin()).append("; almacen retorno: ")
                        .append(ruta.almacenRetorno()).append("; descanso: ").append(ruta.descansoInicio())
                        .append(" / ").append(ruta.descansoFin()).append("\n\n");
                for (var parada : ruta.paradas())
                    s.append("- ").append(parada).append("\n");
                s.append("\nCaminos:\n\n");
                for (var camino : ruta.caminos())
                    s.append("- ").append(camino).append("\n");
                s.append("\n");
            }
        s.append("## Partes pendientes\n\n");
        for (var parte : r.solucion().pendientes())
            s.append("- ").append(parte).append("\n");
        Files.writeString(salida.resolve(r.algoritmo() + "-" + seed + "-" + repeticion + ".md"), s,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    static Path directorio(String nombre) throws Exception {
        Path p = Path.of(nombre);
        if (Files.exists(p))
            try (var f = Files.list(p)) {
                if (f.findAny().isPresent())
                    throw new IllegalArgumentException("Salida debe ser nueva o vacia: " + p);
            }
        Files.createDirectories(p);
        return p;
    }
}
