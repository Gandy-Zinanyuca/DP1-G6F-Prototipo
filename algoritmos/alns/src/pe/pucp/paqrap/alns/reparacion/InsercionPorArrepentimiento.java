package pe.pucp.paqrap.alns.reparacion;

import pe.pucp.paqrap.alns.EvaluadorInsercion;
import pe.pucp.paqrap.alns.EvaluadorInsercion.Insercion;
import pe.pucp.paqrap.alns.OperadorReparacion;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;
import pe.pucp.paqrap.solucion.Solucion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Inserción por arrepentimiento (ISA 5.2).
 *
 * <pre>
 * MIENTRAS removidos no esté vacío
 *     PARA CADA pedido en removidos
 *         inserciones[pedido] ← inserciones factibles (vehículo, posición), por costo ascendente
 *         ≥ 2 elementos → arrepentimiento ← costo(segunda mejor) − costo(mejor)
 *         1 elemento    → arrepentimiento ← arrepentimientoSinAlternativa
 *         0 elementos   → indefinido
 *     pedidoElegido ← mayor arrepentimiento entre los que tienen inserción factible
 *     SI no existe → repartir entre varias unidades el de menor deadline (o marcarlo no
 *                    asignado si tampoco es posible), retirarlo de removidos y continuar
 *     aplicar mejor inserción de pedidoElegido ; retirarlo de removidos
 * </pre>
 *
 * <p>
 * Las inserciones de un pedido en la ruta de una unidad solo cambian cuando esa
 * ruta cambia, así que se memorizan por (pedido, unidad) y, tras cada
 * inserción, se invalida únicamente la unidad modificada (y el inventario del
 * almacén afectado).
 * </p>
 */
public class InsercionPorArrepentimiento implements OperadorReparacion {

    private final double arrepentimientoSinAlternativa;

    public InsercionPorArrepentimiento(double arrepentimientoSinAlternativa) {
        this.arrepentimientoSinAlternativa = arrepentimientoSinAlternativa;
    }

    @Override
    public void reparar(Solucion solucion, List<Pedido> removidos, ContextoPlanificacion ctx, Random aleatorio) {
        List<Pedido> pendientes = new ArrayList<>(removidos);
        Map<Pedido, Map<String, List<Insercion>>> memoria = new HashMap<>();

        while (!pendientes.isEmpty()) {
            Pedido elegido = null;
            Insercion mejorDelElegido = null;
            double mayorArrepentimiento = Double.NEGATIVE_INFINITY;

            for (Pedido p : pendientes) {
                Map<String, List<Insercion>> porUnidad = memoria.computeIfAbsent(p, k -> new LinkedHashMap<>());
                List<Insercion> inserciones = new ArrayList<>();
                for (Vehiculo v : ctx.getUnidadesAsignables()) {
                    List<Insercion> lista = porUnidad.get(v.getCodigo());
                    if (lista == null) {
                        lista = EvaluadorInsercion.factiblesEnUnidad(solucion, p, v, ctx);
                        porUnidad.put(v.getCodigo(), lista);
                    }
                    inserciones.addAll(lista);
                }
                if (inserciones.isEmpty()) {
                    continue; // arrepentimiento indefinido
                }
                inserciones.sort((a, b) -> Double.compare(a.delta, b.delta));

                double arrepentimiento = inserciones.size() >= 2 ? inserciones.get(1).delta - inserciones.get(0).delta
                        : arrepentimientoSinAlternativa;
                if (arrepentimiento > mayorArrepentimiento) {
                    mayorArrepentimiento = arrepentimiento;
                    elegido = p;
                    mejorDelElegido = inserciones.get(0);
                }
            }

            if (elegido == null) {
                // Ninguno cabe completo: se reparte el más urgente entre varias unidades.
                Pedido urgente = pendientes.get(0);
                for (Pedido p : pendientes) {
                    if (p.getMinutoLimite() < urgente.getMinutoLimite()
                            || (p.getMinutoLimite() == urgente.getMinutoLimite() && p.getId() < urgente.getId())) {
                        urgente = p;
                    }
                }
                if (!EvaluadorInsercion.insertarFraccionado(solucion, urgente, ctx)) {
                    solucion.marcarNoAsignado(urgente);
                }
                pendientes.remove(urgente);
                memoria.clear();
                continue;
            }

            EvaluadorInsercion.aplicar(solucion, mejorDelElegido, elegido, ctx);
            pendientes.remove(elegido);
            memoria.remove(elegido);

            // Invalida lo que la inserción pudo cambiar: la ruta modificada y, si el
            // almacén de
            // origen tiene stock limitado, las rutas vacías que podrían abrir desde él.
            String modificada = mejorDelElegido.vehiculo.getCodigo();
            boolean stockLimitado = !mejorDelElegido.almacenOrigen.esCentral();
            for (Map<String, List<Insercion>> porUnidad : memoria.values()) {
                if (stockLimitado) {
                    porUnidad.clear();
                } else {
                    porUnidad.remove(modificada);
                }
            }
        }
    }

    @Override
    public String nombre() {
        return "insercion-arrepentimiento";
    }
}
