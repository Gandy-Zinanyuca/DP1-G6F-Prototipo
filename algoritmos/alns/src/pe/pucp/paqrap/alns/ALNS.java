package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.alns.destruccion.RemocionAleatoria;
import pe.pucp.paqrap.alns.destruccion.RemocionPeor;
import pe.pucp.paqrap.alns.destruccion.RemocionPorArcoBloqueado;
import pe.pucp.paqrap.alns.destruccion.RemocionPorAveria;
import pe.pucp.paqrap.alns.destruccion.RemocionRelacionadaShaw;
import pe.pucp.paqrap.alns.reparacion.InsercionGolosa;
import pe.pucp.paqrap.alns.reparacion.InsercionPorArrepentimiento;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Adaptive Large Neighborhood Search, implementado según el pseudocódigo del Informe de
 * Selección de Algoritmos (ISA v3.0, sección 5.2).
 *
 * <pre>
 * inicioReal ← System.nanoTime()
 * soluciónInicial ← GENERAR_SOLUCIÓN_INICIAL(pedidosConsiderados, ...) ; evaluar
 * SI soluciónInicial no es factible → retornar resultado no factible
 * actual ← mejorGlobal ← soluciónInicial ; inicializar operadores (pesoInicial)
 * MIENTRAS iteración &lt; maxIteraciones
 *     iteración++ ; candidatosEvaluados++
 *     d ← SELECCIONAR_OPERADOR(destrucción) ; r ← SELECCIONAR_OPERADOR(reparación)
 *     grado ← DETERMINAR_GRADO_DESTRUCCIÓN(actual)
 *     (parcial, removidos) ← APLICAR_DESTRUCCIÓN(actual, d, grado)
 *     candidato ← APLICAR_REPARACIÓN(parcial, removidos, r)
 *     SI EVALUAR(candidato) no es factible → candidatosNoFactibles++ ; puntuación ← rechazo
 *     SINO candidatosFactibles++ ; CRITERIO_ACEPTACIÓN
 *          SI acepta: actual ← candidato ; SI mejora mejorGlobal → puntuación ← nuevoMejor
 *     puntuar d y r ; cada tamañoSegmento iteraciones → ACTUALIZAR_PESOS
 * ACTUALIZAR_PESOS ; Ta ← (nanoTime − inicioReal) / 1e6
 * RETORNAR mejorGlobal
 * </pre>
 *
 * <p>Toda la aleatoriedad proviene de un único {@link Random} con semilla configurable, y las
 * colecciones recorridas tienen orden estable: dos ejecuciones con la misma semilla y la misma
 * entrada producen la misma asignación (LE008).</p>
 */
public class ALNS {

    /** Métricas de la ejecución, insumo del informe de experimentación numérica. */
    public static class Estadisticas {
        public int iteraciones;
        public int candidatosEvaluados;
        public int candidatosFactibles;
        public int candidatosNoFactibles;
        public int aceptados;
        public int rechazados;
        public int nuevasMejores;
        public int iteracionMejor;
        /** Ta: tiempo de ejecución del algoritmo, en milisegundos. */
        public long milisegundos;
        public boolean factible;
        public List<String> errores = new ArrayList<>();
        public double costoInicial;
        public double costoFinal;
        public double[] pesosDestruccion = new double[0];
        public double[] pesosReparacion = new double[0];
        public String[] nombresDestruccion = new String[0];
        public String[] nombresReparacion = new String[0];
        public int[] usosDestruccion = new int[0];
        public int[] usosReparacion = new int[0];

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (!factible) {
                sb.append(String.format("ALNS: resultado NO FACTIBLE (Ta=%d ms)%n", milisegundos));
                int mostrar = Math.min(5, errores.size());
                for (int i = 0; i < mostrar; i++) {
                    sb.append("      - ").append(errores.get(i)).append('\n');
                }
                if (errores.size() > mostrar) {
                    sb.append("      ... ").append(errores.size() - mostrar).append(" errores más\n");
                }
                return sb.toString();
            }
            sb.append(String.format("ALNS: %d iteraciones en %d ms | costo S/ %.2f -> S/ %.2f "
                            + "(mejor en iter %d)%n",
                    iteraciones, milisegundos, costoInicial, costoFinal, iteracionMejor));
            sb.append(String.format("      candidatos evaluados=%d factibles=%d noFactibles=%d | "
                            + "aceptados=%d rechazados=%d nuevosMejores=%d%n",
                    candidatosEvaluados, candidatosFactibles, candidatosNoFactibles,
                    aceptados, rechazados, nuevasMejores));
            sb.append("      pesos destrucción:\n");
            for (int i = 0; i < nombresDestruccion.length; i++) {
                sb.append(String.format("        %-28s w=%.3f usos=%d%n",
                        nombresDestruccion[i], pesosDestruccion[i], usosDestruccion[i]));
            }
            sb.append("      pesos reparación:\n");
            for (int i = 0; i < nombresReparacion.length; i++) {
                sb.append(String.format("        %-28s w=%.3f usos=%d%n",
                        nombresReparacion[i], pesosReparacion[i], usosReparacion[i]));
            }
            return sb.toString();
        }
    }

    private final ParametrosALNS par;
    private Estadisticas estadisticas = new Estadisticas();

    public ALNS(ParametrosALNS par) {
        this.par = par;
    }

    /** INICIALIZAR_OPERADORES_DESTRUCCIÓN: los cinco operadores del ISA, en su orden. */
    private static List<OperadorDestruccion> operadoresDestruccion() {
        List<OperadorDestruccion> lista = new ArrayList<>();
        lista.add(new RemocionAleatoria());
        lista.add(new RemocionRelacionadaShaw());
        lista.add(new RemocionPeor());
        lista.add(new RemocionPorArcoBloqueado());
        lista.add(new RemocionPorAveria());
        return lista;
    }

    /** INICIALIZAR_OPERADORES_REPARACIÓN: inserción voraz e inserción por arrepentimiento. */
    private static List<OperadorReparacion> operadoresReparacion(ParametrosALNS par) {
        List<OperadorReparacion> lista = new ArrayList<>();
        lista.add(new InsercionGolosa());
        lista.add(new InsercionPorArrepentimiento(par.arrepentimientoSinAlternativa));
        return lista;
    }

    public Estadisticas getEstadisticas() {
        return estadisticas;
    }

    /**
     * Ejecuta ALNS sobre los pedidos considerados del contexto.
     *
     * @return la mejor solución global; si la solución inicial no es factible, esa solución
     *         (con {@link Solucion#esFactible()} falso y sus errores)
     */
    public Solucion resolver(ContextoPlanificacion ctx) {
        long inicioReal = System.nanoTime();
        estadisticas = new Estadisticas();
        Random aleatorio = new Random(par.semilla);

        Solucion solucionInicial = ConstructorInicial.construir(ctx);
        double costoInicial = solucionInicial.evaluar(ctx);
        estadisticas.costoInicial = costoInicial;

        if (!solucionInicial.esFactible()) {
            estadisticas.factible = false;
            estadisticas.errores = solucionInicial.getErrores();
            estadisticas.milisegundos = (System.nanoTime() - inicioReal) / 1_000_000;
            return solucionInicial;
        }

        Solucion actual = solucionInicial;
        double costoActual = costoInicial;
        Solucion mejorGlobal = solucionInicial.copia();
        double costoMejor = costoInicial;

        SelectorAdaptativo<OperadorDestruccion> destruccion =
                new SelectorAdaptativo<>(operadoresDestruccion(), par.pesoInicial, par.factorReaccion);
        SelectorAdaptativo<OperadorReparacion> reparacion =
                new SelectorAdaptativo<>(operadoresReparacion(par), par.pesoInicial, par.factorReaccion);
        CriterioAceptacion criterio = new CriterioAceptacion(par);

        int iteracion = 0;
        int iteracionMejor = 0;

        while (iteracion < par.maxIteraciones) {
            iteracion++;
            estadisticas.candidatosEvaluados++;

            int iDestruccion = destruccion.seleccionar(aleatorio);
            int iReparacion = reparacion.seleccionar(aleatorio);

            int grado = determinarGradoDestruccion(actual, ctx);

            Solucion candidato = actual.copia();
            List<Pedido> removidos = destruccion.operador(iDestruccion)
                    .destruir(candidato, grado, ctx, aleatorio);
            reparacion.operador(iReparacion).reparar(candidato, removidos, ctx, aleatorio);

            double costoCandidato = candidato.evaluar(ctx);
            double puntuacion;

            if (!candidato.esFactible()) {
                estadisticas.candidatosNoFactibles++;
                puntuacion = par.puntuacionRechazo;
                estadisticas.rechazados++;
            } else {
                estadisticas.candidatosFactibles++;
                CriterioAceptacion.Resultado resultado =
                        criterio.evaluar(costoActual, costoCandidato, iteracion, aleatorio);
                if (resultado.aceptar) {
                    actual = candidato;
                    costoActual = costoCandidato;
                    puntuacion = resultado.puntuacion;
                    estadisticas.aceptados++;

                    if (costoCandidato < costoMejor) {
                        mejorGlobal = candidato.copia();
                        costoMejor = costoCandidato;
                        iteracionMejor = iteracion;
                        puntuacion = par.puntuacionNuevoMejor;
                        estadisticas.nuevasMejores++;
                    }
                } else {
                    puntuacion = par.puntuacionRechazo;
                    estadisticas.rechazados++;
                }
            }

            destruccion.puntuar(iDestruccion, puntuacion);
            reparacion.puntuar(iReparacion, puntuacion);

            if (iteracion % par.tamanioSegmento == 0) {
                destruccion.actualizarPesos();
                reparacion.actualizarPesos();
                if (par.traza) {
                    System.out.printf("  iter %5d  actual=S/ %.2f  mejor=S/ %.2f  T=%.3f%n",
                            iteracion, costoActual, costoMejor, criterio.calcularTemperatura(iteracion));
                }
            }
        }

        destruccion.actualizarPesos();
        reparacion.actualizarPesos();

        mejorGlobal.evaluar(ctx);
        estadisticas.milisegundos = (System.nanoTime() - inicioReal) / 1_000_000;
        estadisticas.iteraciones = iteracion;
        estadisticas.iteracionMejor = iteracionMejor;
        estadisticas.factible = mejorGlobal.esFactible();
        estadisticas.errores = mejorGlobal.getErrores();
        estadisticas.costoFinal = costoMejor;
        estadisticas.pesosDestruccion = destruccion.getPesos();
        estadisticas.pesosReparacion = reparacion.getPesos();
        estadisticas.usosDestruccion = destruccion.getUsosAcumulados();
        estadisticas.usosReparacion = reparacion.getUsosAcumulados();
        estadisticas.nombresDestruccion = destruccion.getOperadores().stream()
                .map(OperadorDestruccion::nombre).toArray(String[]::new);
        estadisticas.nombresReparacion = reparacion.getOperadores().stream()
                .map(OperadorReparacion::nombre).toArray(String[]::new);
        return mejorGlobal;
    }

    /**
     * DETERMINAR_GRADO_DESTRUCCIÓN: grado ← máx(1, redondear(totalPedidos × proporciónDestrucción)).
     *
     * <p>Conforme al ISA, la proporción se reduce dinámicamente con la ocupación de la flota:
     * proporción × (1 − 0,7 · ocupación), cuando la opción está activa.</p>
     */
    private int determinarGradoDestruccion(Solucion s, ContextoPlanificacion ctx) {
        int totalPedidos = s.pedidosAsignados().size();
        double proporcion = par.proporcionDestruccion;
        if (par.destruccionAdaptativaPorOcupacion) {
            proporcion *= (1.0 - 0.7 * ocupacionFlota(s, ctx));
        }
        return (int) Math.max(1, Math.round(totalPedidos * proporcion));
    }

    /** Fracción de la capacidad total de la flota disponible que está comprometida. */
    private static double ocupacionFlota(Solucion s, ContextoPlanificacion ctx) {
        int capacidadTotal = 0;
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            capacidadTotal += v.getCapacidad();
        }
        if (capacidadTotal == 0) {
            return 1.0;
        }
        int cargaComprometida = 0;
        for (Ruta r : s.getRutas()) {
            cargaComprometida += r.getCargaTotal();
        }
        return Math.min(1.0, cargaComprometida / (double) capacidadTotal);
    }
}
