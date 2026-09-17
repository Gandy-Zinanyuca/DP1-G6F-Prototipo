package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.servicios.ConstructorInicial;
import pe.pucp.paqrap.servicios.EvaluadorInsercion;

import pe.pucp.paqrap.alns.destruccion.RemocionAleatoria;
import pe.pucp.paqrap.alns.destruccion.RemocionDeRuta;
import pe.pucp.paqrap.alns.destruccion.RemocionPeor;
import pe.pucp.paqrap.alns.destruccion.RemocionPorArcoBloqueado;
import pe.pucp.paqrap.alns.destruccion.RemocionPorAveria;
import pe.pucp.paqrap.alns.destruccion.RemocionRelacionadaShaw;
import pe.pucp.paqrap.alns.reparacion.InsercionGolosa;
import pe.pucp.paqrap.alns.reparacion.InsercionPorArrepentimiento;
import pe.pucp.paqrap.alns.reparacion.InsercionPorCompatibilidadDeVentanas;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Adaptive Large Neighborhood Search para el componente planificador de PaqRap.
 *
 * <h2>Esquema del algoritmo</h2>
 * <pre>
 * Entrada : contexto del ciclo (pedidos, unidades, almacenes, mapa con bloqueos vigentes)
 * Salida  : asignación de rutas a unidades
 *
 *  1  s   ← construcciónInicial(contexto)          // inserción golosa por criticidad
 *  2  s*  ← s ;  f* ← f(s)
 *  3  w_d ← 1 ;  w_r ← 1                           // pesos de los operadores
 *  4  T   ← calibrarTemperatura(f(s))
 *  5  mientras iter &lt; maxIter y tiempo &lt; presupuesto hacer
 *  6      d ← ruleta(w_d)                          // operador de destrucción
 *  7      r ← ruleta(w_r)                          // operador de reparación
 *  8      q ← gradoDestrucción(s)                  // adaptativo según ocupación de flota
 *  9      s′ ← s copiada
 * 10      R  ← d(s′, q)                            // remover q pedidos
 * 11      r(s′, R ∪ noAsignados(s′))               // reinsertar
 * 12      si f(s′) &lt; f*        entonces s* ← s′ ; s ← s′ ; π ← σ₁
 * 13      sino si f(s′) &lt; f(s) entonces s ← s′ ; π ← σ₂
 * 14      sino si aceptar(f(s′), f(s), T) entonces s ← s′ ; π ← σ₃
 * 15      sino π ← 0
 * 16      w_d[d] += π ; w_r[r] += π                // acumular desempeño
 * 17      T ← T · c                                // enfriamiento
 * 18      cada L iteraciones: w ← λ·w + (1−λ)·π/θ  // actualización adaptativa de pesos
 * 19      si estancado: recalentar y reiniciar desde s*
 * 20  devolver s*
 * </pre>
 *
 * <h2>Por qué este esquema y no otro</h2>
 * <ul>
 *   <li><b>Anytime</b>: la mejor solución conocida existe desde la línea 2, de modo que el
 *       algoritmo puede detenerse en cualquier momento y devolver un plan válido. Es lo que
 *       permite acotarlo al presupuesto de un ciclo de 15 minutos simulados y, a la vez, al de
 *       una simulación de 5 días que debe correr entre 30 y 60 minutos reales.</li>
 *   <li><b>Vecindarios amplios</b>: destruir y reparar reconfigura varias rutas en una sola
 *       iteración. La avería de una unidad cargada se resuelve en un ciclo, sin encadenar
 *       movimientos individuales.</li>
 *   <li><b>Adaptación</b>: los pesos hacen que la cartera de operadores se reajuste sola a la
 *       fase de la operación, sin que haya que reprogramar nada entre escenarios.</li>
 * </ul>
 *
 * <h2>Reproducibilidad</h2>
 * <p>Toda la aleatoriedad proviene de un único {@link Random} con semilla configurable, y todas
 * las colecciones recorridas tienen orden de inserción estable. Dos ejecuciones con la misma
 * semilla y la misma entrada producen exactamente la misma asignación, como exige LE008.</p>
 */
public class ALNS {

    /** Estadísticas de la ejecución, insumo del informe de experimentación numérica. */
    public static class Estadisticas {
        public int iteraciones;
        public int nuevasMejores;
        public int aceptadas;
        public int rechazadas;
        public int recalentamientos;
        public long milisegundos;
        public double costoInicial;
        public double costoFinal;
        public double[] pesosDestruccion;
        public double[] pesosReparacion;
        public String[] nombresDestruccion;
        public String[] nombresReparacion;
        public int[] usosDestruccion;
        public int[] usosReparacion;

        public double mejoraRelativa() {
            return costoInicial <= 0 ? 0 : (costoInicial - costoFinal) / costoInicial;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ALNS: %d iteraciones en %d ms | f0=%.2f -> f*=%.2f (%.1f%% mejor)%n",
                    iteraciones, milisegundos, costoInicial, costoFinal, 100 * mejoraRelativa()));
            sb.append(String.format("      nuevasMejores=%d aceptadas=%d rechazadas=%d recalentamientos=%d%n",
                    nuevasMejores, aceptadas, rechazadas, recalentamientos));
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
    private final SelectorAdaptativo<OperadorDestruccion> destructores;
    private final SelectorAdaptativo<OperadorReparacion> reparadores;
    private final Random aleatorio;
    private Estadisticas estadisticas = new Estadisticas();

    public ALNS(ParametrosALNS par) {
        this.par = par;
        this.aleatorio = new Random(par.semilla);
        this.destructores = new SelectorAdaptativo<>(carteraDestruccion(par), par.factorReaccion);
        this.reparadores = new SelectorAdaptativo<>(carteraReparacion(par), par.factorReaccion);
    }

    /**
     * Cartera de operadores de destrucción. Los dos últimos son aporte propio del equipo,
     * derivados de las incidencias que el enunciado describe como problema crítico.
     */
    private static List<OperadorDestruccion> carteraDestruccion(ParametrosALNS par) {
        List<OperadorDestruccion> lista = new ArrayList<>();
        lista.add(new RemocionAleatoria());
        lista.add(new RemocionPeor(par.sesgoRemocionPeor));
        lista.add(new RemocionRelacionadaShaw(par.shawPesoDistancia, par.shawPesoTiempo,
                par.shawPesoCarga, par.sesgoRemocionShaw));
        lista.add(new RemocionDeRuta());
        lista.add(new RemocionPorArcoBloqueado());
        lista.add(new RemocionPorAveria());
        return lista;
    }

    private static List<OperadorReparacion> carteraReparacion(ParametrosALNS par) {
        List<OperadorReparacion> lista = new ArrayList<>();
        lista.add(new InsercionGolosa(0.0));
        lista.add(new InsercionGolosa(par.factorRuido));
        for (int k : par.ordenesArrepentimiento) {
            lista.add(new InsercionPorArrepentimiento(k, par.factorRuido));
        }
        lista.add(new InsercionPorCompatibilidadDeVentanas());
        return lista;
    }

    public Estadisticas getEstadisticas() {
        return estadisticas;
    }

    /** Resuelve el ciclo partiendo de una solución construida desde cero. */
    public Solucion resolver(ContextoPlanificacion ctx) {
        return resolver(ctx, null);
    }

    /**
     * Resuelve el ciclo, opcionalmente partiendo de un plan previo.
     *
     * <p>Pasar el plan del ciclo anterior es lo que convierte al algoritmo en un
     * <b>reoptimizador</b> en lugar de un optimizador desde cero: ante un bloqueo o una avería,
     * la búsqueda arranca del plan vigente y solo destruye lo que quedó comprometido, que es
     * exactamente la reasignación de productos en camino que el enunciado describe.</p>
     */
    public Solucion resolver(ContextoPlanificacion ctx, Solucion planPrevio) {
        long inicio = System.currentTimeMillis();
        estadisticas = new Estadisticas();

        Solucion actual = (planPrevio != null)
                ? heredar(planPrevio, ctx)
                : ConstructorInicial.construir(ctx);
        double costoActual = actual.evaluar(ctx);

        Solucion mejor = actual.copia();
        double costoMejor = costoActual;

        estadisticas.costoInicial = costoActual;

        CriterioAceptacion aceptacion = new CriterioAceptacion(
                escalaTemperatura(actual, ctx), par.porcentajeAceptacionInicial,
                par.temperaturaFinalRelativa, par.maxIteraciones);

        int sinMejora = 0;
        int iteracion = 0;

        while (iteracion < par.maxIteraciones
                && System.currentTimeMillis() - inicio < par.presupuestoMs) {

            int iDestructor = destructores.seleccionar(aleatorio);
            int iReparador = reparadores.seleccionar(aleatorio);

            Solucion candidata = actual.copia();
            int q = gradoDestruccion(candidata, ctx);

            destructores.operador(iDestructor).destruir(candidata, q, ctx, aleatorio);

            // Se reinsertan tanto los recién removidos como los que ya estaban sin asignar:
            // un pedido que no cupo en una iteración puede caber tras la siguiente destrucción.
            List<Pedido> porInsertar = seleccionarParaReinsertar(candidata, ctx);
            reparadores.operador(iReparador).reparar(candidata, porInsertar, ctx, aleatorio);

            // Los pedidos que ningún reparador pudo colocar sin tardanza se despachan igual:
            // la política de PaqRap no admite cancelar entregas.
            EvaluadorInsercion.colocarRezagados(candidata, ctx, porInsertar);

            double costoCandidata = candidata.evaluar(ctx);
            double puntaje;

            if (costoCandidata < costoMejor - 1e-9) {
                mejor = candidata.copia();
                costoMejor = costoCandidata;
                actual = candidata;
                costoActual = costoCandidata;
                puntaje = par.puntajeNuevoMejor;
                estadisticas.nuevasMejores++;
                sinMejora = 0;
            } else if (costoCandidata < costoActual - 1e-9) {
                actual = candidata;
                costoActual = costoCandidata;
                puntaje = par.puntajeMejora;
                estadisticas.aceptadas++;
                sinMejora++;
            } else if (aceptacion.aceptar(costoCandidata, costoActual, aleatorio)) {
                actual = candidata;
                costoActual = costoCandidata;
                puntaje = par.puntajeAceptada;
                estadisticas.aceptadas++;
                sinMejora++;
            } else {
                puntaje = par.puntajeRechazada;
                estadisticas.rechazadas++;
                sinMejora++;
            }

            destructores.premiar(iDestructor, puntaje);
            reparadores.premiar(iReparador, puntaje);
            aceptacion.enfriar();

            iteracion++;
            if (iteracion % par.longitudSegmento == 0) {
                destructores.actualizarPesos();
                reparadores.actualizarPesos();
                if (par.traza) {
                    System.out.printf("  iter %5d  f=%.2f  f*=%.2f  T=%.1f%n",
                            iteracion, costoActual, costoMejor, aceptacion.getTemperatura());
                }
            }
            if (sinMejora >= par.iteracionesParaRecalentar) {
                aceptacion.recalentar(0.25);
                actual = mejor.copia();
                costoActual = costoMejor;
                sinMejora = 0;
                estadisticas.recalentamientos++;
            }
        }

        mejor.evaluar(ctx);
        estadisticas.iteraciones = iteracion;
        estadisticas.milisegundos = System.currentTimeMillis() - inicio;
        estadisticas.costoFinal = costoMejor;
        estadisticas.pesosDestruccion = destructores.getPesos();
        estadisticas.pesosReparacion = reparadores.getPesos();
        estadisticas.usosDestruccion = destructores.getUsosAcumulados();
        estadisticas.usosReparacion = reparadores.getUsosAcumulados();
        estadisticas.nombresDestruccion = destructores.getOperadores().stream()
                .map(OperadorDestruccion::nombre).toArray(String[]::new);
        estadisticas.nombresReparacion = reparadores.getOperadores().stream()
                .map(OperadorReparacion::nombre).toArray(String[]::new);
        return mejor;
    }

    /**
     * Adapta el plan del ciclo anterior al contexto actual: conserva las asignaciones que
     * siguen siendo válidas, libera los pedidos de unidades que dejaron de estar disponibles
     * (LE098) y añade los pedidos nuevos como no asignados.
     */
    private Solucion heredar(Solucion planPrevio, ContextoPlanificacion ctx) {
        Solucion s = new Solucion();
        List<String> asignables = new ArrayList<>();
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            asignables.add(v.getCodigo());
        }
        List<Pedido> vigentes = ctx.getPedidosPorAtender();

        for (Ruta anterior : planPrevio.getRutas()) {
            if (anterior.estaVacia() || !asignables.contains(anterior.getVehiculo().getCodigo())) {
                continue;   // unidad averiada o en mantenimiento: sus pedidos se replanifican
            }
            Ruta nueva = s.rutaDe(anterior.getVehiculo(), anterior.getAlmacenOrigen());
            for (Pedido p : anterior.getSecuencia()) {
                if (vigentes.contains(p) && s.hayStock(ctx, anterior.getAlmacenOrigen(), p.getCantidad())) {
                    s.asignar(nueva, nueva.tamanio(), p);
                }
            }
            nueva.recalcular(ctx);
        }
        for (Pedido p : vigentes) {
            if (s.unidadDe(p) == null) {
                s.marcarNoAsignado(p);
            }
        }
        return s;
    }

    /**
     * Grado de destrucción q: cuántos pedidos se remueven en la iteración.
     *
     * <p>Se sortea una fracción en [ρ_min, ρ_max] del total de pedidos del ciclo. Cuando la
     * adaptación por ocupación está activa, esa fracción se contrae en proporción a la carga
     * comprometida de la flota:</p>
     * <pre>
     *   ρ_efectivo = ρ · (1 − 0,7 · ocupación)
     * </pre>
     * <p>Con la flota casi vacía el algoritmo destruye mucho y explora agresivamente; con la
     * flota saturada —la antesala del colapso— destruye poco, porque una solución apenas
     * factible difícilmente se reconstruye si se la desarma a fondo. Esta es la mitigación
     * concreta de la limitación que el informe de selección le reconoce al ALNS cerca del
     * límite de factibilidad.</p>
     */
    private int gradoDestruccion(Solucion s, ContextoPlanificacion ctx) {
        int total = s.pedidosAsignados().size() + s.getNoAsignados().size();
        if (total == 0) {
            return 0;
        }
        double fraccion = par.gradoDestruccionMin
                + aleatorio.nextDouble() * (par.gradoDestruccionMax - par.gradoDestruccionMin);

        if (par.destruccionAdaptativaPorOcupacion) {
            fraccion *= (1.0 - 0.7 * ocupacionFlota(s, ctx));
        }
        int q = (int) Math.round(fraccion * total);
        q = Math.max(par.destruccionMinimaAbsoluta, q);
        q = Math.min(par.destruccionMaximaAbsoluta, q);
        return Math.min(q, total);
    }

    /**
     * Conjunto de pedidos que el reparador intentará colocar en esta iteración.
     *
     * <p>Incluye los que el destructor acaba de remover y, además, los que ya venían sin
     * asignar: un pedido que no cupo antes puede caber tras la destrucción actual, y excluirlo
     * lo condenaría a quedar fuera para siempre.</p>
     *
     * <p>El conjunto se acota a los más críticos porque el costo de una reparación crece de
     * forma lineal con su tamaño. Cuando la demanda supera con holgura la capacidad de la flota
     * —el escenario de colapso—, intentar reinsertar miles de pedidos en cada iteración reduce
     * el número de iteraciones a unas pocas y anula la búsqueda. Priorizar por holgura concentra
     * el esfuerzo donde puede evitarse un incumplimiento.</p>
     */
    private List<Pedido> seleccionarParaReinsertar(Solucion s, ContextoPlanificacion ctx) {
        List<Pedido> candidatos = new ArrayList<>(s.getNoAsignados());
        if (candidatos.size() <= par.maxPedidosPorReparacion) {
            return candidatos;
        }
        final int ahora = ctx.getMinutoActual();
        candidatos.sort(java.util.Comparator
                .comparingInt((Pedido p) -> p.holgura(ahora))
                .thenComparingInt(Pedido::getId));
        return new ArrayList<>(candidatos.subList(0, par.maxPedidosPorReparacion));
    }

    /**
     * Escala sobre la que se calibra la temperatura inicial del recocido simulado.
     *
     * <p>No se usa f(s₀) completa, y la razón es importante. En este problema f(s₀) suele estar
     * dominada por un término <b>irreducible</b>: los pedidos que ya vencieron su plazo antes
     * del ciclo, o los que ninguna unidad alcanza a tiempo, aportan cientos de miles de puntos
     * que ninguna reordenación puede eliminar. Calibrar la temperatura sobre esa magnitud haría
     * que el criterio aceptara prácticamente cualquier candidata y el ALNS degeneraría en una
     * caminata aleatoria.</p>
     *
     * <p>Se calibra entonces sobre la <b>parte mejorable</b> de la función objetivo —el costo
     * de operación más el costo fijo de las unidades en servicio—, con lo que se obtiene el
     * comportamiento deseado: la búsqueda explora libremente entre soluciones que difieren en
     * kilómetros, pero rechaza casi siempre las que añaden un incumplimiento o dejan un pedido
     * sin despachar. Es la estructura lexicográfica de la función objetivo trasladada al
     * criterio de aceptación.</p>
     */
    private double escalaTemperatura(Solucion s, ContextoPlanificacion ctx) {
        double base = s.getCostoOperacionSoles()
                + ctx.getParametros().costoFijoPorUnidad * s.numeroUnidadesUsadas();
        return Math.max(base, ctx.getParametros().costoFijoPorUnidad);
    }

    /** Fracción de la capacidad total de la flota asignable que está comprometida. */
    private double ocupacionFlota(Solucion s, ContextoPlanificacion ctx) {
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

    /** Nombres de los operadores, para el informe de experimentación numérica. */
    public List<String> nombresOperadores() {
        List<String> nombres = new ArrayList<>();
        for (OperadorDestruccion d : destructores.getOperadores()) {
            nombres.add("D:" + d.nombre());
        }
        for (OperadorReparacion r : reparadores.getOperadores()) {
            nombres.add("R:" + r.nombre());
        }
        return nombres;
    }

    @Override
    public String toString() {
        return "ALNS" + Arrays.toString(nombresOperadores().toArray());
    }
}
