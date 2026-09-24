package pe.pucp.paqrap.alns;

import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Ruta;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.List;

/**
 * Primitiva compartida por la solución inicial y los operadores de reparación: dado un pedido,
 * enumerar las inserciones <b>factibles</b> (vehículo, posición) y su costo.
 *
 * <p>Sigue el bloque común de GENERAR_SOLUCIÓN_INICIAL y APLICAR_REPARACIÓN del ISA:</p>
 * <pre>
 * PARA CADA vehículo disponible
 *     SI pedido.cantidad supera capacidad disponible → continuar
 *     PARA posición desde 0 hasta tamaño de la ruta
 *         candidato ← insertar pedido en posición ; evaluar candidato
 *         SI candidato es factible → registrar (candidato, costo)
 * </pre>
 * <p>Como insertar un pedido solo modifica una ruta, "evaluar candidato" se resuelve evaluando
 * esa ruta (restricciones duras de {@link Ruta#recalcular}) y el inventario del almacén de
 * origen; el costo del candidato difiere del de la solución en Δ = costo(ruta') − costo(ruta).
 * Para una unidad ociosa se prueba además cada almacén de origen con stock suficiente.</p>
 *
 * <p>Con recargas, la ruta de una unidad puede tener varios viajes: un pedido cabe en la unidad
 * si cabe en un viaje, y la posición donde se inserta determina si la unidad sigue repartiendo
 * o vuelve antes a recargar. El inventario se verifica después de insertar, porque una ruta puede
 * tomar stock de varios almacenes.</p>
 *
 * <p>Una unidad que ya transporta una parte del mismo pedido no recibe otra: repartir el pedido
 * solo tiene sentido entre unidades distintas. Los pedidos pueden dividirse entre vehículos:
 * {@link #insertar} elige entre insertarlo completo y repartirlo ({@link #insertarFraccionado})
 * según cuál cuesta menos.</p>
 */
public final class EvaluadorInsercion {

    /** Inserción factible: dónde va el pedido y cuánto aumenta el costo de la solución. */
    public static class Insercion {
        public final Vehiculo vehiculo;
        public final int posicion;
        public final Almacen almacenOrigen;
        public final double delta;
        /**
         * La inserción obliga a la unidad a una recarga más (un viaje más en una ruta que ya
         * tenía). Estrenar una unidad ociosa no cuenta: usar la flota libre no es un sobrecosto.
         */
        public final boolean abreViaje;

        public Insercion(Vehiculo vehiculo, int posicion, Almacen almacenOrigen, double delta,
                         boolean abreViaje) {
            this.vehiculo = vehiculo;
            this.posicion = posicion;
            this.almacenOrigen = almacenOrigen;
            this.delta = delta;
            this.abreViaje = abreViaje;
        }
    }

    /** Cómo quedó insertado un pedido. */
    public enum Resultado {
        /** No admite inserción factible, ni completo ni repartido. */
        NINGUNA,
        /** Completo en una unidad. */
        COMPLETO,
        /** Repartido entre varias unidades. */
        FRACCIONADO
    }

    private EvaluadorInsercion() {
    }

    /** Todas las inserciones factibles del pedido en la ruta de una unidad concreta. */
    public static List<Insercion> factiblesEnUnidad(Solucion s, Pedido p, Vehiculo v,
                                                    ContextoPlanificacion ctx) {
        List<Insercion> lista = new ArrayList<>();
        Ruta r = s.getRuta(v.getCodigo());
        if (r != null && r.contienePedido(p)) {
            return lista;   // la unidad ya lleva una parte de este pedido
        }
        if (p.getCantidad() > capacidadLibre(r, v, ctx)) {
            return lista;   // no cabe ni en un viaje nuevo (o en la carga libre, sin recargas)
        }
        if (r != null && ctx.getParametros().permitirRecargas
                && r.getCargaTotal() + p.getCantidad()
                   > (long) v.getCapacidad() * ctx.getParametros().maxViajesPorRuta) {
            return lista;   // no cabe ni usando todos los viajes permitidos
        }

        if (r == null || r.estaVacia()) {
            Ruta prueba = new Ruta(v, ctx.getAlmacenes().get(0));
            prueba.usarInventarioDe(s);
            for (Almacen a : ctx.getAlmacenes()) {
                prueba.setAlmacenOrigen(a);
                prueba.insertar(0, p);
                prueba.recalcular(ctx);
                if (prueba.esFactible() && s.stockAlcanza(ctx, prueba)) {
                    lista.add(new Insercion(v, 0, a, prueba.costoOperacion(), false));
                }
                prueba.remover(0);
            }
            return lista;
        }

        r.asegurarCalculada(ctx);
        double base = r.costoOperacion();
        int viajesBase = r.getViajes().size();
        // Llegadas sin comidas: la inserción puede mover las comidas, pero nunca adelantar una
        // llegada por debajo de esta cota.
        int[] llegadasBase = r.getLlegadasSinComida();
        for (int pos = 0; pos <= r.tamanio(); pos++) {
            // Si la parada previa ya llega después de la hora límite del pedido, insertarlo en
            // esta posición o en cualquiera posterior lo haría llegar tarde.
            if (pos > 0 && pos - 1 < llegadasBase.length
                    && llegadasBase[pos - 1] > p.getMinutoLimite()) {
                break;
            }
            r.insertar(pos, p);
            r.recalcular(ctx);
            if (r.esFactible() && s.stockAlcanza(ctx, r)) {
                lista.add(new Insercion(v, pos, r.getAlmacenOrigen(), r.costoOperacion() - base,
                        r.getViajes().size() > viajesBase));
            }
            r.remover(pos);
        }
        r.recalcular(ctx);   // restaura los atributos derivados de la ruta original
        return lista;
    }

    /**
     * Cantidad máxima que la unidad puede recibir de una parte: su capacidad completa si puede
     * recargar (la parte irá en algún viaje), o la capacidad libre de su único viaje si no.
     */
    static int capacidadLibre(Ruta r, Vehiculo v, ContextoPlanificacion ctx) {
        if (ctx.getParametros().permitirRecargas) {
            return v.getCapacidad();
        }
        return v.getCapacidad() - ((r == null) ? 0 : r.getCargaTotal());
    }

    /**
     * Tamaños de fracción que vale la pena probar en la unidad: lo que cabe en el espacio libre de
     * cada uno de sus viajes (aprovecharlo no obliga a recargar) y un viaje completo, sin superar
     * lo que falta repartir ni lo que la unidad puede llevar con todos sus viajes permitidos.
     */
    static List<Integer> tamaniosDeFraccion(Ruta r, Vehiculo v, int restante, ContextoPlanificacion ctx) {
        int capacidad = v.getCapacidad();
        int carga = (r == null) ? 0 : r.getCargaTotal();
        boolean recargas = ctx.getParametros().permitirRecargas;
        int presupuesto = recargas
                ? capacidad * ctx.getParametros().maxViajesPorRuta - carga
                : capacidad - carga;
        int tope = Math.min(restante, presupuesto);
        List<Integer> tamanios = new ArrayList<>();
        if (tope <= 0) {
            return tamanios;
        }
        tamanios.add(Math.min(tope, capacidad));
        if (recargas && r != null && !r.estaVacia()) {
            for (Ruta.Viaje viaje : r.getViajes()) {
                int q = Math.min(tope, capacidad - viaje.getCarga());
                if (q > 0 && !tamanios.contains(q)) {
                    tamanios.add(q);
                }
            }
        }
        return tamanios;
    }

    /** Todas las inserciones factibles del pedido en la solución, en orden estable. */
    public static List<Insercion> insercionesFactibles(Solucion s, Pedido p, ContextoPlanificacion ctx) {
        List<Insercion> lista = new ArrayList<>();
        for (Vehiculo v : ctx.getUnidadesAsignables()) {
            lista.addAll(factiblesEnUnidad(s, p, v, ctx));
        }
        return lista;
    }

    /** Mejor inserción factible (menor costo) del pedido, o {@code null} si no existe. */
    public static Insercion mejorInsercion(Solucion s, Pedido p, ContextoPlanificacion ctx) {
        Insercion mejor = null;
        for (Insercion ins : insercionesFactibles(s, p, ctx)) {
            if (mejor == null || ins.delta < mejor.delta) {
                mejor = ins;
            }
        }
        return mejor;
    }

    /**
     * Inserta el pedido: completo en la mejor posición o, si ninguna unidad lo admite completo,
     * repartido entre varias. Con {@code fraccionarSiConviene} también se reparte cuando cuesta
     * menos que insertarlo completo; en ese caso solo se prueba cuando la inserción completa
     * obliga a la unidad a recargar una vez más: si cabe en un viaje existente, partirlo solo
     * agregaría paradas, y si va a una unidad ociosa, la flota libre se usa antes que repartir.
     * (Comparar contra estrenar una unidad —cuyo costo incluye todo su recorrido— hacía que
     * repartir pareciera barato en lo local y empeoraba el plan: más km y más fracciones.)
     *
     * @param mejorCompleta la mejor inserción completa ({@link #mejorInsercion}), o {@code null}
     */
    public static Resultado insertar(Solucion s, Pedido p, Insercion mejorCompleta, ContextoPlanificacion ctx) {
        if (mejorCompleta != null && (!mejorCompleta.abreViaje || !ctx.getParametros().fraccionarSiConviene)) {
            aplicar(s, mejorCompleta, p, ctx);
            return Resultado.COMPLETO;
        }
        double tope = (mejorCompleta == null) ? Double.POSITIVE_INFINITY : mejorCompleta.delta;
        if (insertarFraccionado(s, p, ctx, tope)) {
            return Resultado.FRACCIONADO;
        }
        if (mejorCompleta != null) {
            aplicar(s, mejorCompleta, p, ctx);
            return Resultado.COMPLETO;
        }
        return Resultado.NINGUNA;
    }

    /** Como {@link #insertar(Solucion, Pedido, Insercion, ContextoPlanificacion)}, buscando la mejor. */
    public static Resultado insertar(Solucion s, Pedido p, ContextoPlanificacion ctx) {
        return insertar(s, p, mejorInsercion(s, p, ctx), ctx);
    }

    /**
     * Reparte el pedido entre varias unidades.
     *
     * <pre>
     * restante ← cantidad del pedido ; costo ← 0
     * MIENTRAS restante &gt; 0
     *     PARA CADA vehículo disponible que no lleve ya parte del pedido
     *         PARA CADA tamaño q (espacio libre de sus viajes, un viaje completo)
     *             evaluar la mejor inserción factible de una fracción de q unidades
     *     elegir la de menor costo por unidad (desempate: mayor q) y aplicarla
     *     costo ← costo + Δ ; restante ← restante − q
     *     SI no existe ninguna o costo ≥ tope → deshacer las fracciones aplicadas y fallar
     * </pre>
     *
     * <p>El costo por unidad compara fracciones de distinto tamaño: una fracción chica que
     * aprovecha el espacio libre de un viaje que ya pasa cerca cuesta poco por unidad; una que
     * abre un viaje nuevo, mucho.</p>
     *
     * @param tope costo de la alternativa (insertarlo completo); repartir debe costar menos
     * @return verdadero si el pedido quedó completamente asignado en fracciones; si es falso,
     *         la solución queda como estaba
     */
    public static boolean insertarFraccionado(Solucion s, Pedido p, ContextoPlanificacion ctx, double tope) {
        int restante = p.getCantidad();
        double costo = 0;
        List<Pedido> aplicadas = new ArrayList<>();
        while (restante > 0) {
            Insercion mejor = null;
            Pedido mejorFraccion = null;
            for (Vehiculo v : ctx.getUnidadesAsignables()) {
                Ruta r = s.getRuta(v.getCodigo());
                for (int q : tamaniosDeFraccion(r, v, restante, ctx)) {
                    Pedido fraccion = (q == p.getCantidad()) ? p : p.fraccion(q);
                    for (Insercion ins : factiblesEnUnidad(s, fraccion, v, ctx)) {
                        if (mejor == null || esMejorFraccion(ins, q, mejor, mejorFraccion.getCantidad())) {
                            mejor = ins;
                            mejorFraccion = fraccion;
                        }
                    }
                }
            }
            if (mejor == null || costo + mejor.delta >= tope) {
                for (Pedido f : aplicadas) {
                    s.olvidar(f);
                }
                return false;
            }
            aplicar(s, mejor, mejorFraccion, ctx);
            aplicadas.add(mejorFraccion);
            costo += mejor.delta;
            restante -= mejorFraccion.getCantidad();
        }
        if (!aplicadas.contains(p)) {
            s.olvidar(p);   // el pedido quedó representado por sus fracciones
        }
        return true;
    }

    /** Reparte el pedido sin alternativa con qué compararlo (ninguna unidad lo admite completo). */
    public static boolean insertarFraccionado(Solucion s, Pedido p, ContextoPlanificacion ctx) {
        return insertarFraccionado(s, p, ctx, Double.POSITIVE_INFINITY);
    }

    private static boolean esMejorFraccion(Insercion ins, int q, Insercion mejor, int qMejor) {
        double porUnidad = ins.delta / q;
        double porUnidadMejor = mejor.delta / qMejor;
        if (Math.abs(porUnidad - porUnidadMejor) > 1e-9) {
            return porUnidad < porUnidadMejor;
        }
        return q > qMejor;
    }

    /** Aplica una inserción sobre la solución. */
    public static void aplicar(Solucion s, Insercion ins, Pedido p, ContextoPlanificacion ctx) {
        Ruta ruta = s.rutaDe(ins.vehiculo, ins.almacenOrigen);
        if (ruta.estaVacia() && ruta.getAlmacenOrigen() != ins.almacenOrigen) {
            s.cambiarAlmacenOrigen(ruta, ins.almacenOrigen);
        }
        s.asignar(ruta, ins.posicion, p);
        ruta.recalcular(ctx);
    }
}
