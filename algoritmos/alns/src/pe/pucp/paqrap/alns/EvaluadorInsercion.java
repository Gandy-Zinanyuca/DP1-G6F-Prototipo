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
        int cargaActual = (r == null) ? 0 : r.getCargaTotal();
        if (cargaActual + p.getCantidad() > v.getCapacidad()) {
            return lista;   // supera la capacidad disponible
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

        if (!s.hayStock(ctx, r.getAlmacenOrigen(), p.getCantidad())) {
            return lista;
        }
        r.asegurarCalculada(ctx);
        double base = r.costoOperacion();
        for (int pos = 0; pos <= r.tamanio(); pos++) {
            r.insertar(pos, p);
            r.recalcular(ctx);
            if (r.esFactible()) {
                lista.add(new Insercion(v, pos, r.getAlmacenOrigen(), r.costoOperacion() - base));
            }
            r.remover(pos);
        }
        r.recalcular(ctx);   // restaura los atributos derivados de la ruta original
        return lista;
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
