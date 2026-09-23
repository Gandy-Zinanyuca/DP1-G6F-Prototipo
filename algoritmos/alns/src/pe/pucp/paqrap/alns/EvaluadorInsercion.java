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
 * solo tiene sentido entre unidades distintas. Cuando ninguna unidad admite el pedido completo,
 * {@link #insertarFraccionado} lo reparte entre varias (los pedidos pueden dividirse entre
 * vehículos).</p>
 */
public final class EvaluadorInsercion {

    /** Inserción factible: dónde va el pedido y cuánto aumenta el costo de la solución. */
    public static class Insercion {
        public final Vehiculo vehiculo;
        public final int posicion;
        public final Almacen almacenOrigen;
        public final double delta;

        public Insercion(Vehiculo vehiculo, int posicion, Almacen almacenOrigen, double delta) {
            this.vehiculo = vehiculo;
            this.posicion = posicion;
            this.almacenOrigen = almacenOrigen;
            this.delta = delta;
        }
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
            for (Almacen a : ctx.getAlmacenes()) {
                if (!s.hayStock(ctx, a, p.getCantidad())) {
                    continue;
                }
                prueba.setAlmacenOrigen(a);
                prueba.insertar(0, p);
                prueba.recalcular(ctx);
                if (prueba.esFactible()) {
                    lista.add(new Insercion(v, 0, a, prueba.costoOperacion()));
                }
                prueba.remover(0);
            }
            return lista;
        }

        if (!ctx.getParametros().permitirRecargas && !s.hayStock(ctx, r.getAlmacenOrigen(), p.getCantidad())) {
            return lista;
        }
        r.asegurarCalculada(ctx);
        double base = r.costoOperacion();
        int[] llegadasBase = r.getMinutosLlegada().clone();
        boolean baseFactible = r.esFactible();
        for (int pos = 0; pos <= r.tamanio(); pos++) {
            // Si la parada previa ya llega después de la hora límite del pedido, insertarlo en
            // esta posición o en cualquiera posterior lo haría llegar tarde.
            if (baseFactible && pos > 0 && pos - 1 < llegadasBase.length
                    && llegadasBase[pos - 1] > p.getMinutoLimite()) {
                break;
            }
            r.insertar(pos, p);
            r.recalcular(ctx);
            if (r.esFactible() && s.stockAlcanza(ctx, r)) {
                lista.add(new Insercion(v, pos, r.getAlmacenOrigen(), r.costoOperacion() - base));
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
     * Reparte el pedido entre varias unidades cuando ninguna lo admite completo.
     *
     * <pre>
     * restante ← cantidad del pedido
     * MIENTRAS restante &gt; 0
     *     PARA CADA vehículo disponible que no lleve ya parte del pedido
     *         q ← mín(restante, capacidad libre del vehículo)
     *         evaluar la mejor inserción factible de una fracción de q unidades
     *     elegir la fracción de mayor q (desempate: menor costo) y aplicarla
     *     SI no existe ninguna → deshacer las fracciones aplicadas y fallar
     *     restante ← restante − q
     * </pre>
     *
     * <p>Preferir la fracción más grande minimiza el número de partes. La capacidad es la única
     * restricción que depende de la cantidad (además del stock), así que q no necesita
     * explorarse por debajo de la capacidad libre.</p>
     *
     * @return verdadero si el pedido quedó completamente asignado en fracciones; si es falso,
     *         la solución queda como estaba
     */
    public static boolean insertarFraccionado(Solucion s, Pedido p, ContextoPlanificacion ctx) {
        int restante = p.getCantidad();
        List<Pedido> aplicadas = new ArrayList<>();
        while (restante > 0) {
            Insercion mejor = null;
            Pedido mejorFraccion = null;
            for (Vehiculo v : ctx.getUnidadesAsignables()) {
                Ruta r = s.getRuta(v.getCodigo());
                int q = Math.min(restante, capacidadLibre(r, v, ctx));
                if (q <= 0) {
                    continue;
                }
                if (mejorFraccion != null && q < mejorFraccion.getCantidad()) {
                    continue;
                }
                Pedido fraccion = (q == p.getCantidad()) ? p : p.fraccion(q);
                for (Insercion ins : factiblesEnUnidad(s, fraccion, v, ctx)) {
                    boolean mayor = mejorFraccion == null || q > mejorFraccion.getCantidad();
                    if (mayor || ins.delta < mejor.delta) {
                        mejor = ins;
                        mejorFraccion = fraccion;
                    }
                }
            }
            if (mejor == null) {
                for (Pedido f : aplicadas) {
                    s.olvidar(f);
                }
                return false;
            }
            aplicar(s, mejor, mejorFraccion, ctx);
            aplicadas.add(mejorFraccion);
            restante -= mejorFraccion.getCantidad();
        }
        if (!aplicadas.contains(p)) {
            s.olvidar(p);   // el pedido quedó representado por sus fracciones
        }
        return true;
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
