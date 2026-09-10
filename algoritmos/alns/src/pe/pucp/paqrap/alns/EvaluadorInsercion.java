package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.planificador.ParametrosPlanificador;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.List;

/**
 * Primitiva compartida por todos los operadores de reparación: dado un pedido, encontrar en
 * qué ruta y en qué posición conviene insertarlo.
 *
 * <p>Esta clase concentra la <b>verificación de factibilidad</b> del problema. Es la
 * consecuencia práctica de la propiedad que motivó la elección del ALNS en el informe de
 * selección de algoritmos: las restricciones de PaqRap —capacidad por tipo de unidad, plazos de
 * 4/8/12/18/36 horas, turnos, hora de alimentación e inventario de los almacenes intermedios—
 * se absorben aquí, en el operador de inserción, sin reformular el modelo. Añadir una
 * restricción nueva significa añadir una comprobación en este archivo, no rediseñar el
 * algoritmo.</p>
 *
 * <h2>Costo de inserción</h2>
 * <p>El costo de insertar el pedido <i>p</i> en la posición <i>k</i> de la ruta <i>r</i> es el
 * incremento de la contribución de <i>r</i> a la función objetivo:</p>
 * <pre>
 *   Δ(p, r, k) = contribución(r ⊕ (p,k)) − contribución(r)
 *   contribución(r) = costoFijo + w_d·costoOperación(r) + w_t·tardanza(r)
 * </pre>
 *
 * <h2>Costo computacional</h2>
 * <p>Evaluar todas las posiciones de una ruta de n paradas cuesta O(n²): n+1 posiciones por una
 * pasada de recálculo O(n) cada una. Las podas por capacidad e inventario descartan rutas en
 * O(1) antes de recalcular nada. Las rutas vacías prueban además cada almacén de origen, que es
 * lo que le da al algoritmo su carácter multi-almacén: es en ese punto donde se decide de qué
 * almacén sale cada unidad.</p>
 */
public final class EvaluadorInsercion {

    /** Candidata de inserción: dónde va el pedido y cuánto cuesta ponerlo ahí. */
    public static class Insercion {
        public final Ruta ruta;
        public final int posicion;
        public final Almacen almacenOrigen;
        public final double delta;
        public final boolean admisible;
        /**
         * Holgura mínima que quedaría en la ruta tras la inserción, en minutos: el menor
         * margen entre la llegada estimada y la hora límite de cualquiera de sus pedidos.
         * Es la medida de compatibilidad de ventanas que usa el reparador homónimo.
         */
        public final int holguraResultante;

        public Insercion(Ruta ruta, int posicion, Almacen almacenOrigen,
                         double delta, boolean admisible, int holguraResultante) {
            this.ruta = ruta;
            this.posicion = posicion;
            this.almacenOrigen = almacenOrigen;
            this.delta = delta;
            this.admisible = admisible;
            this.holguraResultante = holguraResultante;
        }
    }

    private EvaluadorInsercion() {
    }

    /** Menor margen entre llegada estimada y hora límite en toda la ruta ya recalculada. */
    private static int holguraMinima(Ruta r) {
        int minima = Integer.MAX_VALUE;
        int[] llegadas = r.getMinutosLlegada();
        for (int i = 0; i < r.tamanio(); i++) {
            minima = Math.min(minima, r.getSecuencia().get(i).getMinutoLimite() - llegadas[i]);
        }
        return minima;
    }

    /**
     * Contribución de una ruta ya recalculada a la función objetivo. Debe mantenerse
     * consistente con {@link Solucion#evaluar}: si divergen, el ALNS optimizaría una función
     * distinta de la que se reporta.
     */
    public static double contribucion(Ruta r, ParametrosPlanificador par) {
        if (r.estaVacia()) {
            return 0.0;
        }
        double c = par.costoFijoPorUnidad
                + par.pesoCostoDistancia * r.costoOperacion()
                + par.penalizacionPedidoTardio * r.getPedidosTardios()
                + par.penalizacionPorMinutoTardanza * r.getTardanzaTotalMinutos();
        if (!r.esFactible()) {
            // Ruta estructuralmente inviable (capacidad, turno o nodo aislado): se penaliza
            // como si todos sus pedidos incumplieran, para que la búsqueda la desarme.
            c += par.penalizacionPedidoTardio * r.tamanio();
        }
        return c;
    }

    /**
     * Mejor inserción del pedido en la ruta de una unidad concreta, o {@code null} si no existe
     * ninguna admisible. Distingue dos casos: si la unidad ya tiene ruta se prueban todas sus
     * posiciones; si está ociosa se abre una ruta nueva probando cada almacén de origen.
     *
     * @param soloAdmisibles si es verdadero, descarta las inserciones que produzcan tardanza o
     *                       violen una restricción dura; si es falso, devuelve la mejor aunque
     *                       sea infactible, lo que permite al ALNS atravesar regiones no
     *                       admisibles para alcanzar mejores soluciones
     */
    public static Insercion mejorEnUnidad(Solucion s, Pedido p, Vehiculo v,
                                          ContextoPlanificacion ctx, boolean soloAdmisibles) {
        if (p.getCantidad() > v.getCapacidad()) {
            return null;
        }
        Ruta r = s.getRuta(v.getCodigo());
        if (r == null || r.estaVacia()) {
            return mejorEnRutaVacia(s, p, v, r, ctx, soloAdmisibles);
        }
        // Podas O(1) antes de tocar el mapa.
        if (r.getCargaTotal() + p.getCantidad() > v.getCapacidad()) {
            return null;
        }
        if (!s.hayStock(ctx, r.getAlmacenOrigen(), p.getCantidad())) {
            return null;
        }
        return mejorPosicionEn(r, p, ctx, soloAdmisibles);
    }

    /** Una candidata por unidad asignable, sin ordenar. */
    public static List<Insercion> porUnidad(Solucion s, Pedido p, ContextoPlanificacion ctx,
                                            boolean soloAdmisibles) {
        List<Insercion> candidatas = new ArrayList<>();
        if (ctx.getMapa().nodoAislado(p.getDestino())) {
            return candidatas;   // destino incomunicado por bloqueos: no hay inserción posible
        }
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            Insercion ins = mejorEnUnidad(s, p, v, ctx, soloAdmisibles);
            if (ins != null) {
                candidatas.add(ins);
            }
        }
        return candidatas;
    }

    /** Mejor inserción del pedido en toda la solución. */
    public static Insercion mejorInsercion(Solucion s, Pedido p, ContextoPlanificacion ctx,
                                           boolean soloAdmisibles) {
        Insercion mejor = null;
        for (Insercion ins : porUnidad(s, p, ctx, soloAdmisibles)) {
            if (mejor == null || ins.delta < mejor.delta) {
                mejor = ins;
            }
        }
        return mejor;
    }

    /**
     * Prueba todas las posiciones de una ruta no vacía. La ruta se modifica y se restaura, de
     * modo que al retornar queda exactamente en el estado en que llegó.
     */
    private static Insercion mejorPosicionEn(Ruta r, Pedido p, ContextoPlanificacion ctx,
                                             boolean soloAdmisibles) {
        ParametrosPlanificador par = ctx.getParametros();
        double base = contribucion(r, par);
        double mejorDelta = Double.POSITIVE_INFINITY;
        int mejorPos = -1;
        boolean mejorAdmisible = false;
        int mejorHolgura = Integer.MIN_VALUE;

        for (int pos = 0; pos <= r.tamanio(); pos++) {
            r.insertar(pos, p);
            r.recalcular(ctx);
            boolean admisible = r.esFactible() && r.getTardanzaTotalMinutos() == 0;
            if (!soloAdmisibles || admisible) {
                double delta = contribucion(r, par) - base;
                if (delta < mejorDelta) {
                    mejorDelta = delta;
                    mejorPos = pos;
                    mejorAdmisible = admisible;
                    mejorHolgura = holguraMinima(r);
                }
            }
            r.remover(pos);
        }
        r.recalcular(ctx);   // restaura los atributos derivados de la ruta original

        if (mejorPos < 0) {
            return null;
        }
        return new Insercion(r, mejorPos, r.getAlmacenOrigen(), mejorDelta,
                mejorAdmisible, mejorHolgura);
    }

    /**
     * Abre una ruta nueva para una unidad ociosa: prueba cada almacén de origen con stock
     * suficiente y elige el que produzca menor costo.
     */
    private static Insercion mejorEnRutaVacia(Solucion s, Pedido p, Vehiculo v, Ruta existente,
                                              ContextoPlanificacion ctx, boolean soloAdmisibles) {
        ParametrosPlanificador par = ctx.getParametros();
        Ruta r = existente != null ? existente : new Ruta(v, ctx.getAlmacenes().get(0));
        Almacen origenPrevio = r.getAlmacenOrigen();

        double mejorDelta = Double.POSITIVE_INFINITY;
        Almacen mejorAlmacen = null;
        boolean mejorAdmisible = false;
        int mejorHolgura = Integer.MIN_VALUE;

        for (Almacen a : ctx.almacenesPorCercania(v.getPosicion())) {
            if (!s.hayStock(ctx, a, p.getCantidad())) {
                continue;
            }
            r.setAlmacenOrigen(a);
            r.insertar(0, p);
            r.recalcular(ctx);
            boolean admisible = r.esFactible() && r.getTardanzaTotalMinutos() == 0;
            if (!soloAdmisibles || admisible) {
                double delta = contribucion(r, par);   // la ruta vacía contribuía 0
                if (delta < mejorDelta) {
                    mejorDelta = delta;
                    mejorAlmacen = a;
                    mejorAdmisible = admisible;
                    mejorHolgura = holguraMinima(r);
                }
            }
            r.remover(0);
        }
        r.setAlmacenOrigen(origenPrevio);
        r.recalcular(ctx);

        if (mejorAlmacen == null) {
            return null;
        }
        Ruta destino = s.rutaDe(v, mejorAlmacen);
        return new Insercion(destino, 0, mejorAlmacen, mejorDelta, mejorAdmisible, mejorHolgura);
    }

    /**
     * Coloca los pedidos que ningún operador pudo insertar sin tardanza, admitiendo ahora que
     * lleguen fuera de plazo.
     *
     * <p>Es una decisión de negocio, no un artificio numérico: un pedido que ya venció su hora
     * límite —porque llegó en un ciclo anterior o porque el bloqueo lo dejó fuera de alcance—
     * <b>igual debe entregarse</b>. La política de PaqRap no admite cancelar entregas. La
     * función objetivo penaliza el incumplimiento, y es el simulador quien decide, en el
     * escenario de colapso, que la ejecución termina (LE021).</p>
     *
     * @return número de pedidos que se pudieron colocar en esta pasada
     */
    public static int colocarRezagados(Solucion s, ContextoPlanificacion ctx) {
        return colocarRezagados(s, ctx, new ArrayList<>(s.getNoAsignados()));
    }

    /** Variante acotada: solo intenta colocar los pedidos del subconjunto indicado. */
    public static int colocarRezagados(Solucion s, ContextoPlanificacion ctx,
                                       List<Pedido> candidatos) {
        int colocados = 0;
        for (Pedido p : candidatos) {
            if (s.unidadDe(p) != null) {
                continue;   // ya fue colocado por el reparador
            }
            Insercion ins = mejorInsercion(s, p, ctx, false);
            if (ins != null) {
                aplicar(s, ins, p, ctx);
                colocados++;
            }
        }
        return colocados;
    }

    /**
     * Materializa una inserción en la solución, fijando antes el almacén de origen si la
     * candidata proponía uno distinto del actual.
     */
    public static void aplicar(Solucion s, Insercion ins, Pedido p, ContextoPlanificacion ctx) {
        if (ins.ruta.estaVacia() && ins.almacenOrigen != ins.ruta.getAlmacenOrigen()) {
            s.cambiarAlmacenOrigen(ins.ruta, ins.almacenOrigen);
        }
        s.asignar(ins.ruta, ins.posicion, p);
        ins.ruta.recalcular(ctx);
    }
}
