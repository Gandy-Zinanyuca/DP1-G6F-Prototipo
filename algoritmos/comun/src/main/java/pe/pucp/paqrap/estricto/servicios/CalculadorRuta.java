package pe.pucp.paqrap.estricto.servicios;

import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.caminos.*;
import java.time.*;
import java.util.*;

/**
 * Horarios con caminos temporales, pausa en paradas y retorno de llegada
 * minima.
 */
public final class CalculadorRuta {
  private final EstadoOperacion estado;
  private final ParametrosOperacion par;
  private final PathFinder finder;

  private record Consulta(Nodo a, Nodo b, LocalDateTime t, double velocidad) {
  }

  private final Map<Consulta, Camino> caminos = new LinkedHashMap<>(256, .75f, true) {
    protected boolean removeEldestEntry(Map.Entry<Consulta, Camino> e) {
      return size() > 4000;
    }
  };

  public CalculadorRuta(EstadoOperacion e, ParametrosOperacion p) {
    estado = e;
    par = p;
    finder = new PathFinder(new GridMap(e.bloqueos()));
  }

  private Camino camino(Nodo a, Nodo b, LocalDateTime t, Vehiculo v) {
    var q = new Consulta(a, b, t, par.velocidades().get(v.tipo()));
    return caminos.computeIfAbsent(q, k -> finder.buscar(a, b, t, q.velocidad()));
  }

  public ResultadoRuta calcular(Ruta r) {
    var v = estado.vehiculos().stream().filter(x -> x.codigo().equals(r.vehiculo())).findFirst().orElseThrow();
    var origen = estado.almacenes().stream().filter(x -> x.id().equals(r.almacenOrigen())).findFirst().orElseThrow();
    LocalDateTime salida = estado.instante().isAfter(v.disponibleDesde()) ? estado.instante() : v.disponibleDesde();
    if (r.partes().isEmpty())
      return new ResultadoRuta(r, salida, salida, origen.id(), List.of(), List.of(), null, null, 0, 0, List.of());
    if (r.carga() > v.tipo().capacidad())
      return error(r, salida, "capacidad");
    if (!v.disponible())
      return error(r, salida, "vehiculo indisponible");
    int minutoDia = salida.getHour() * 60 + salida.getMinute();
    LocalDateTime turno = salida.toLocalDate().atStartOfDay().plusMinutes(par.inicioTurnoMinuto() +
        Math.floorDiv(minutoDia - par.inicioTurnoMinuto(), par.turnoMinutos()) * par.turnoMinutos());
    LocalDateTime finTurno = turno.plusMinutes(par.turnoMinutos());
    var grupos = new ArrayList<List<PartePedido>>();
    for (var parte : r.partes()) {
      if (grupos.isEmpty() || !grupos.get(grupos.size() - 1).get(0).pedido().id().equals(parte.pedido().id()))
        grupos.add(new ArrayList<>());
      grupos.get(grupos.size() - 1).add(parte);
    }
    // descansoRealizado solo describe el turno del snapshot.
    boolean descansoHecho = estado.descansoRealizado().contains(v.codigo()) && !turno.isAfter(estado.instante());
    if (salida.plusMinutes((long) grupos.size() * par.servicioMinutos() + (descansoHecho ? 0 : par.descansoMinutos()))
        .isAfter(finTurno))
      return error(r, salida, "servicio y descanso exceden turno");
    ResultadoRuta mejor = null, fallo = null;
    for (int pausa = descansoHecho ? -2 : -1; pausa <= (descansoHecho ? -2 : grupos.size()); pausa++) {
      ResultadoRuta candidata = simular(r, v, origen, salida, turno, grupos, pausa);
      if (candidata.factible() && (mejor == null || candidata.costo() < mejor.costo() ||
          (candidata.costo() == mejor.costo() && candidata.fin().isBefore(mejor.fin()))))
        mejor = candidata;
      fallo = candidata;
    }
    return mejor != null ? mejor : fallo;
  }

  private ResultadoRuta simular(Ruta r, Vehiculo v, Almacen origen, LocalDateTime salida, LocalDateTime turno,
      List<List<PartePedido>> grupos, int pausa) {
    var trazas = new ArrayList<Camino>();
    var paradas = new ArrayList<Parada>();
    LocalDateTime hora = salida, di = null, df = null;
    Nodo nodo = v.ubicacionInicial();
    double distancia = 0;
    LocalDateTime bandaIni = turno.plusMinutes(par.descansoDesde()), bandaFin = turno.plusMinutes(par.descansoHasta());
    // -1: antes de desplazarse al almacen; 0..n: en el almacen o despues de una
    // visita.
    if (pausa == -1) {
      di = hora.isBefore(bandaIni) ? bandaIni : hora;
      df = di.plusMinutes(par.descansoMinutos());
      if (df.isAfter(bandaFin))
        return error(r, salida, "descanso fuera de banda");
      hora = df;
    }
    if (!r.enCurso()) {
      var c = camino(nodo, origen.nodo(), hora, v);
      trazas.add(c);
      distancia += c.distanciaKm();
      hora = c.llegada();
      nodo = origen.nodo();
    }
    for (int i = 0; i <= grupos.size(); i++) {
      if (pausa == i) {
        di = hora.isBefore(bandaIni) ? bandaIni : hora;
        df = di.plusMinutes(par.descansoMinutos());
        if (df.isAfter(bandaFin))
          return error(r, salida, "descanso fuera de banda");
        hora = df;
      }
      if (i == grupos.size())
        break;
      var grupo = grupos.get(i);
      var pedido = grupo.get(0).pedido();
      var c = camino(nodo, pedido.ubicacion(), hora, v);
      trazas.add(c);
      distancia += c.distanciaKm();
      var fin = c.llegada().plusMinutes(par.servicioMinutos());
      if ((par.plazoIncluyeServicio() ? fin : c.llegada()).isAfter(pedido.deadline()))
        return error(r, salida, "plazo duro: " + pedido.id());
      paradas.add(new Parada(grupo, c.llegada(), fin));
      hora = fin;
      nodo = pedido.ubicacion();
      if (hora.isAfter(turno.plusMinutes(par.turnoMinutos())))
        return error(r, salida, "turno excedido");
    }
    Camino retorno = null;
    Almacen almacen = null;
    for (var a : estado.almacenes()) {
      var c = camino(nodo, a.nodo(), hora, v);
      if (retorno == null || c.llegada().isBefore(retorno.llegada()) ||
          (c.llegada().equals(retorno.llegada()) && c.distanciaKm() < retorno.distanciaKm())) {
        retorno = c;
        almacen = a;
      }
    }
    trazas.add(retorno);
    distancia += retorno.distanciaKm();
    hora = retorno.llegada();
    if (hora.isAfter(turno.plusMinutes(par.turnoMinutos())))
      return error(r, salida, "retorno fuera de turno");
    if (!GestorDisponibilidad.disponible(estado, v, salida, hora))
      return error(r, salida, "averia o mantenimiento solapado");
    return new ResultadoRuta(r, salida, hora, almacen.id(), paradas, trazas, di, df, distancia,
        par.costoFijoVehiculo() + distancia * v.tipo().costoPorKm(), List.of());
  }

  private ResultadoRuta error(Ruta r, LocalDateTime t, String error) {
    return new ResultadoRuta(r, t, t, r.almacenOrigen(), List.of(), List.of(), null, null, 0, 0,
        List.of(r.vehiculo() + ": " + error));
  }
}
