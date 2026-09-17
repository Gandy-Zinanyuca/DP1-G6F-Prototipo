package pe.pucp.paqrap;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;
import pe.pucp.paqrap.alns.*;
import pe.pucp.paqrap.datos.*;
import pe.pucp.paqrap.modelo.*;
import pe.pucp.paqrap.planificador.*;
import pe.pucp.paqrap.servicios.ConstructorInicial;
import pe.pucp.paqrap.solucion.*;

/** Verificación de un ciclo con archivos del mismo mes y presupuesto por iteraciones. */
public final class PruebaDatosReales {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5) {
            throw new IllegalArgumentException("Uso: PruebaDatosReales ventas.AAAAMM.txt bloqueo.AAMM.txt mantenimiento.txt [dia] [hora]");
        }
        Path ventas = Path.of(args[0]), bloqueos = Path.of(args[1]), mantenimiento = Path.of(args[2]);
        var fecha = Pattern.compile("ventas\\.(\\d{4})(\\d{2})\\.txt").matcher(ventas.getFileName().toString());
        if (!fecha.matches()) throw new IllegalArgumentException("Nombre de ventas sin período AAAAMM");
        int anio = Integer.parseInt(fecha.group(1)), mes = Integer.parseInt(fecha.group(2));
        YearMonth periodo = YearMonth.of(anio, mes);
        String esperado = String.format(Locale.ROOT, "bloqueo.%02d%02d.txt", anio % 100, mes);
        if (!bloqueos.getFileName().toString().equals(esperado)) {
            throw new IllegalArgumentException("El bloqueo debe corresponder al período: " + esperado);
        }
        int dia = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int hora = args.length > 4 ? Integer.parseInt(args[4]) : 8;
        periodo.atDay(dia);
        if (hora < 0 || hora > 23) throw new IllegalArgumentException("Hora fuera de rango");
        var v = CargadorVentas.cargar(ventas);
        var b = CargadorBloqueos.cargar(bloqueos);
        var m = CargadorMantenimiento.cargar(mantenimiento);
        System.out.printf("Período=%s día=%d hora=%d%nVentas=%d omitidas=%d; bloqueos=%d omitidos=%d; mantenimientos=%d%n",
                periodo, dia, hora, v.pedidos.size(), v.omitidos, b.bloqueos.size(), b.omitidos, m.size());
        if (v.omitidos != 0 || b.omitidos != 0) {
            throw new IllegalArgumentException("Registros omitidos: " + v.motivos + " / " + b.motivos);
        }
        Instancia instancia = Instancia.construir(ventas, bloqueos, mantenimiento, anio, mes,
                Almacen.CAPACIDAD_INTERMEDIO_POR_DEFECTO, 10, 15, 12);
        ContextoPlanificacion ctx = ContextoPlanificacion.construir(instancia,
                Turnos.aMinutos(dia, hora, 0), instancia.getPedidos(), List.of(), new ParametrosPlanificador());
        System.out.printf("Pedidos del ciclo=%d; unidades asignables=%d; bloqueos vigentes=%d; mantenimiento del día=%s%n",
                ctx.getPedidosPorAtender().size(), ctx.getUnidadesAsignables().size(),
                ctx.getMapa().getBloqueosVigentes().size(), instancia.unidadesEnMantenimiento(dia));
        if (ctx.getPedidosPorAtender().isEmpty()) throw new IllegalStateException("Ciclo sin pedidos");
        ParametrosALNS p = new ParametrosALNS();
        p.maxIteraciones = 200;
        p.presupuestoMs = Long.MAX_VALUE;
        System.out.printf("Semilla=%d; iteraciones=%d; sin parada por reloj%n", p.semilla, p.maxIteraciones);
        double inicial = ConstructorInicial.construir(ctx).evaluar(ctx);
        ALNS motor = new ALNS(p);
        Solucion s = motor.resolver(ctx);
        Solucion repetida = new ALNS(p).resolver(ctx);
        Set<Integer> esperados = new HashSet<>(), encontrados = new HashSet<>();
        for (Pedido pedido : ctx.getPedidosPorAtender()) esperados.add(pedido.getId());
        for (Pedido pedido : s.getNoAsignados()) comprobar(encontrados.add(pedido.getId()), "Pedido duplicado");
        Set<String> disponibles = new HashSet<>();
        for (Vehiculo unidad : ctx.getUnidadesAsignables()) disponibles.add(unidad.getCodigo());
        for (Ruta ruta : s.getRutas()) {
            if (!ruta.estaVacia()) comprobar(disponibles.contains(ruta.getVehiculo().getCodigo()), "Unidad no disponible");
            comprobar(ruta.getCargaTotal() <= ruta.getVehiculo().getCapacidad(), "Capacidad excedida");
            for (Pedido pedido : ruta.getSecuencia()) comprobar(encontrados.add(pedido.getId()), "Pedido duplicado");
        }
        comprobar(esperados.equals(encontrados), "Pedidos perdidos o ajenos al ciclo");
        for (Almacen almacen : ctx.getAlmacenes()) comprobar(s.consumo(almacen) <= ctx.stockInicial(almacen), "Inventario excedido");
        comprobar(s.getCosto() <= inicial + 1e-6, "Empeora solución inicial");
        comprobar(s.toString().equals(repetida.toString()), "Repetición diferente");
        System.out.printf(Locale.ROOT, "Costo inicial=%.2f; costo ALNS=%.2f; distancia=%.2f km; operación=%.2f soles%n",
                inicial, s.getCosto(), s.getDistanciaTotalKm(), s.getCostoOperacionSoles());
        System.out.printf("Asignados=%d; diferidos=%d; tardíos=%d; unidades usadas=%d; admisible=%s%n",
                s.pedidosAsignados().size(), s.getNoAsignados().size(), s.getPedidosTardios(),
                s.numeroUnidadesUsadas(), s.esAdmisible());
        System.out.printf("Tiempo primera búsqueda=%d ms; iteraciones=%d%n",
                motor.getEstadisticas().milisegundos, motor.getEstadisticas().iteraciones);
        System.out.println("OK: carga, integridad, capacidad, inventario, disponibilidad, no empeoramiento y reproducibilidad.");
        System.out.println("La integridad no garantiza cumplimiento de plazos ni factibilidad operacional completa.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
