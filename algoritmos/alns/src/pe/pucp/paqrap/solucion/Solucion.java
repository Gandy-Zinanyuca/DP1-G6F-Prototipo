package pe.pucp.paqrap.solucion;

import pe.pucp.paqrap.modelo.Almacen;
import pe.pucp.paqrap.modelo.Pedido;
import pe.pucp.paqrap.modelo.Vehiculo;
import pe.pucp.paqrap.planificador.ContextoPlanificacion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solución completa de un ciclo de planificación: una ruta por unidad utilizada más el
 * conjunto de pedidos que quedaron sin asignar.
 *
 * <h2>Estructura de datos</h2>
 * <pre>
 * Solucion
 *   ├── rutas          : LinkedHashMap&lt;codigoUnidad, Ruta&gt;   (orden estable ⇒ reproducible)
 *   ├── noAsignados    : LinkedHashSet&lt;Pedido&gt;
 *   ├── ubicacion      : IdentityHashMap&lt;parte, codigoUnidad&gt; (índice inverso, O(1))
 *   └── (consumo por almacén: derivado de los viajes de cada ruta, control de LE019)
 * </pre>
 *
 * <p>El índice inverso {@code ubicacion} es lo que hace baratos los operadores de destrucción:
 * remover un pedido no exige recorrer todas las rutas, sino localizar la suya en O(1). El
 * inventario acopla varias rutas —es una restricción global—: cada ruta calcula cuánto toma de
 * cada almacén en sus viajes y la solución suma esos consumos.</p>
 *
 * <p>Las rutas transportan <i>partes</i>: pedidos completos o fracciones de un pedido (ver
 * {@link Pedido#fraccion}). Un mismo pedido puede repartirse entre varias rutas, por eso el
 * índice inverso usa la identidad del objeto y EVALUAR verifica la cobertura por cantidad.</p>
 *
 * <p>El uso de colecciones con orden de inserción estable (LinkedHashMap / LinkedHashSet) no
 * es cosmético: junto con una semilla fija del generador aleatorio, es lo que permite que dos
 * ejecuciones del mismo escenario produzcan resultados idénticos, como exigen LE008 y LE009.</p>
 */
public class Solucion {

    private final Map<String, Ruta> rutas = new LinkedHashMap<>();
    private final Set<Pedido> noAsignados = new LinkedHashSet<>();
    private final Map<Pedido, String> ubicacion = new IdentityHashMap<>();

    private double costo = Double.NaN;
    private List<String> errores;
    private double distanciaTotalKm;
    private double costoOperacionSoles;
    private int pedidosPostergados;
    private int paquetesPostergados;

    public Solucion() {
    }

    /** Copia profunda: cada ruta se clona, de modo que los operadores no comparten estado. */
    public Solucion copia() {
        Solucion s = new Solucion();
        for (Map.Entry<String, Ruta> e : rutas.entrySet()) {
            s.rutas.put(e.getKey(), e.getValue().copia());
        }
        s.noAsignados.addAll(noAsignados);
        s.ubicacion.putAll(ubicacion);
        s.costo = costo;
        s.errores = errores == null ? null : new ArrayList<>(errores);
        s.distanciaTotalKm = distanciaTotalKm;
        s.costoOperacionSoles = costoOperacionSoles;
        s.pedidosPostergados = pedidosPostergados;
        s.paquetesPostergados = paquetesPostergados;
        return s;
    }

    // ------------------------------------------------------------------ estructura

    public Collection<Ruta> getRutas() {
        return rutas.values();
    }

    public Ruta getRuta(String codigoUnidad) {
        return rutas.get(codigoUnidad);
    }

    public Ruta getRuta(Vehiculo v) {
        return rutas.get(v.getCodigo());
    }

    /** Devuelve la ruta de la unidad, creándola vacía sobre el almacén indicado si no existía. */
    public Ruta rutaDe(Vehiculo v, Almacen almacenPorDefecto) {
        Ruta r = rutas.get(v.getCodigo());
        if (r == null) {
            r = new Ruta(v, almacenPorDefecto);
            rutas.put(v.getCodigo(), r);
        }
        return r;
    }

    public Set<Pedido> getNoAsignados() {
        return noAsignados;
    }

    public void marcarNoAsignado(Pedido p) {
        noAsignados.add(p);
        ubicacion.remove(p);
    }

    /**
     * Retira la parte de la solución sin dejarla como no asignada: se usa cuando la parte deja
     * de existir porque se fusionó con otras o se repartió en fracciones.
     */
    public void olvidar(Pedido p) {
        if (ubicacion.containsKey(p)) {
            desasignar(p);
        }
        noAsignados.remove(p);
    }

    /**
     * Fusiona las partes removidas que pertenecen a un mismo pedido en una sola, para que la
     * reparación las reinserte juntas y solo vuelva a fraccionar si hace falta. Las partes
     * fusionadas se reemplazan en el conjunto de no asignados por la parte resultante.
     *
     * @return las partes a reinsertar, en el orden de la primera aparición de cada pedido
     */
    public List<Pedido> consolidar(List<Pedido> removidos) {
        Map<Pedido, List<Pedido>> porOriginal = new LinkedHashMap<>();
        for (Pedido p : removidos) {
            List<Pedido> partes = porOriginal.computeIfAbsent(p.getOriginal(), k -> new ArrayList<>());
            if (!partes.contains(p)) {
                partes.add(p);
            }
        }
        List<Pedido> resultado = new ArrayList<>(porOriginal.size());
        for (Map.Entry<Pedido, List<Pedido>> e : porOriginal.entrySet()) {
            List<Pedido> partes = e.getValue();
            if (partes.size() == 1) {
                resultado.add(partes.get(0));
                continue;
            }
            int total = 0;
            for (Pedido parte : partes) {
                total += parte.getCantidad();
                olvidar(parte);
            }
            Pedido original = e.getKey();
            Pedido fusion = (total == original.getCantidad()) ? original : original.fraccion(total);
            noAsignados.add(fusion);
            resultado.add(fusion);
        }
        return resultado;
    }

    /** Unidad que transporta el pedido, o {@code null} si está sin asignar. */
    public String unidadDe(Pedido p) {
        return ubicacion.get(p);
    }

    /**
     * Inserta el pedido en la posición indicada de la ruta de la unidad, actualizando el índice
     * inverso. No verifica factibilidad: el llamador ya la comprobó al evaluar el costo de
     * inserción.
     */
    public void asignar(Ruta ruta, int posicion, Pedido pedido) {
        ruta.insertar(posicion, pedido);
        noAsignados.remove(pedido);
        ubicacion.put(pedido, ruta.getVehiculo().getCodigo());
    }

    /**
     * Extrae el pedido de la ruta que lo contiene y lo deja sin asignar. Es la operación
     * elemental de todos los operadores de destrucción del ALNS.
     *
     * @return la ruta de la que fue removido, o {@code null} si el pedido no estaba asignado
     */
    public Ruta desasignar(Pedido pedido) {
        String codigo = ubicacion.remove(pedido);
        if (codigo == null) {
            return null;
        }
        Ruta r = rutas.get(codigo);
        if (r != null) {
            r.remover(pedido);
        }
        noAsignados.add(pedido);
        return r;
    }

    /** Pedidos actualmente asignados a alguna ruta. */
    public List<Pedido> pedidosAsignados() {
        List<Pedido> lista = new ArrayList<>();
        for (Ruta r : rutas.values()) {
            lista.addAll(r.getSecuencia());
        }
        return lista;
    }

    public int numeroUnidadesUsadas() {
        int n = 0;
        for (Ruta r : rutas.values()) {
            if (!r.estaVacia()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ inventario (LE019)

    /** Unidades que las rutas de la solución toman del almacén, sumando todos sus viajes. */
    public int consumo(ContextoPlanificacion ctx, Almacen almacen) {
        int total = 0;
        for (Ruta r : rutas.values()) {
            if (!r.estaVacia()) {
                r.asegurarCalculada(ctx);
                total += r.consumoEn(almacen);
            }
        }
        return total;
    }

    /** Verifica que el almacén pueda soportar una carga adicional sin dejar stock negativo. */
    public boolean hayStock(ContextoPlanificacion ctx, Almacen almacen, int cantidad) {
        if (almacen.esCentral()) {
            return true;
        }
        return ctx.stockInicial(almacen) - consumo(ctx, almacen) >= cantidad;
    }

    /** Verifica que ningún almacén intermedio quede con stock negativo con las rutas actuales. */
    public boolean stockAlcanza(ContextoPlanificacion ctx) {
        for (Almacen a : ctx.getAlmacenes()) {
            if (!a.esCentral() && consumo(ctx, a) > ctx.stockInicial(a)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Como {@link #stockAlcanza(ContextoPlanificacion)}, pero tras modificar una sola ruta de una
     * solución que ya respetaba el inventario: solo pueden haberse excedido los almacenes
     * intermedios de los que esa ruta toma carga.
     */
    public boolean stockAlcanza(ContextoPlanificacion ctx, Ruta modificada) {
        modificada.asegurarCalculada(ctx);
        for (Almacen a : ctx.getAlmacenes()) {
            if (!a.esCentral() && modificada.consumoEn(a) > 0 && consumo(ctx, a) > ctx.stockInicial(a)) {
                return false;
            }
        }
        return true;
    }

    /** Cambia el almacén de origen (del primer viaje) de una ruta. */
    public void cambiarAlmacenOrigen(Ruta ruta, Almacen nuevo) {
        ruta.setAlmacenOrigen(nuevo);
    }

    // ------------------------------------------------------------------ evaluación

    /**
     * EVALUAR(solución) del ISA (sección 5.1): comprueba todas las restricciones duras y
     * calcula el costo.
     *
     * <p>La solución es factible si y solo si no se registra ningún error: toda ruta es factible
     * (capacidad, plazos, caminos, mantenimiento, alimentación y turno), la cantidad pendiente
     * de cada pedido considerado queda cubierta exactamente por las partes asignadas (un pedido
     * puede repartirse entre varias rutas), no hay pedidos adicionales ni partes sin asignar, y
     * ningún almacén intermedio queda con stock negativo. El costo es
     * Σ distanciaRuta × costoKmVehículo y solo se usa para comparar soluciones factibles.</p>
     *
     * <p>Reprogramación: una parte sin asignar cuyo pedido todavía tiene holgura suficiente
     * ({@link ContextoPlanificacion#esPostergable}) no es un error —se atenderá en un ciclo
     * posterior—, pero cada paquete reprogramado suma la penalización configurada al costo.</p>
     *
     * @return el costo total de la solución
     */
    public double evaluar(ContextoPlanificacion ctx) {
        errores = new ArrayList<>();
        distanciaTotalKm = 0;
        costoOperacionSoles = 0;

        Set<Pedido> partesVistas = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Pedido, Integer> cubierto = new IdentityHashMap<>();
        for (Ruta r : rutas.values()) {
            r.asegurarCalculada(ctx);
            if (r.estaVacia()) {
                continue;
            }
            if (!r.esFactible()) {
                errores.add(r.getVehiculo().getCodigo() + ": " + r.getMotivoInfactibilidad());
            }
            for (Pedido p : r.getSecuencia()) {
                if (!partesVistas.add(p)) {
                    errores.add("pedido duplicado P" + p.getId());
                }
                cubierto.merge(p.getOriginal(), p.getCantidad(), Integer::sum);
            }
            distanciaTotalKm += r.getDistanciaKm();
            costoOperacionSoles += r.costoOperacion();
        }

        pedidosPostergados = 0;
        paquetesPostergados = 0;
        java.util.Set<Pedido> originalesPostergados = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Pedido p : noAsignados) {
            if (ctx.esPostergable(p)) {
                paquetesPostergados += p.getCantidad();
                if (originalesPostergados.add(p.getOriginal())) {
                    pedidosPostergados++;
                }
            } else {
                errores.add("pedido no asignado P" + p.getId());
            }
        }
        Map<Pedido, Integer> sinAsignar = new IdentityHashMap<>();
        for (Pedido p : noAsignados) {
            sinAsignar.merge(p.getOriginal(), p.getCantidad(), Integer::sum);
        }
        for (Pedido p : ctx.getPedidosPorAtender()) {
            int requerido = p.getCantidad();
            int asignado = cubierto.getOrDefault(p.getOriginal(), 0);
            if (asignado > requerido) {
                errores.add("cantidad excedida de P" + p.getId() + " (" + asignado + ">" + requerido + ")");
            } else if (asignado + sinAsignar.getOrDefault(p.getOriginal(), 0) < requerido) {
                errores.add("pedido faltante P" + p.getId());
            }
        }
        for (Pedido original : cubierto.keySet()) {
            if (ctx.cantidadRequerida(original) == 0) {
                errores.add("pedido adicional P" + original.getId());
            }
        }

        for (Almacen a : ctx.getAlmacenes()) {
            if (!a.esCentral() && consumo(ctx, a) > ctx.stockInicial(a)) {
                errores.add("stock insuficiente en " + a.getId());
            }
        }

        costo = costoOperacionSoles
                + ctx.getParametros().penalizacionPorPaquetePostergado * paquetesPostergados;
        return costo;
    }

    /** Resultado de la última evaluación: verdadero si no se registró ningún error. */
    public boolean esFactible() {
        return errores != null && errores.isEmpty();
    }

    /** Errores de la última evaluación (restricciones duras violadas). */
    public List<String> getErrores() {
        return errores == null ? new ArrayList<>() : errores;
    }

    public double getCosto() {
        return costo;
    }

    public double getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    /** Pedidos (originales) que el plan reprograma para un ciclo posterior. */
    public int getPedidosPostergados() {
        return pedidosPostergados;
    }

    /** Paquetes que el plan reprograma para un ciclo posterior. */
    public int getPaquetesPostergados() {
        return paquetesPostergados;
    }

    public double getCostoOperacionSoles() {
        return costoOperacionSoles;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Solución  %s  costo=S/ %.2f  unidades=%d  km=%.0f  noAsignados=%d%n",
                esFactible() ? "FACTIBLE" : "NO FACTIBLE", costo, numeroUnidadesUsadas(),
                distanciaTotalKm, noAsignados.size()));
        for (Ruta r : rutas.values()) {
            if (!r.estaVacia()) {
                sb.append(r).append('\n');
            }
        }
        return sb.toString();
    }
}
