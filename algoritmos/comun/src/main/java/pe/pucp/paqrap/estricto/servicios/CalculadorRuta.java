package pe.pucp.paqrap.estricto.servicios;

import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.caminos.*;
import java.time.*;
import java.util.*;

/**
 * Horarios con caminos temporales, pausa en paradas, espera al siguiente turno
 * cuando la ruta ya no cabe en el vigente y retorno de llegada minima.
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
    // bloquearNodos=true: un nodo bloqueado no se puede atravesar ni admite giro lateral (solo permite
    // volver por donde se llego), cerrando todas las calles incidentes a ese nodo durante el bloqueo.
    finder = new PathFinder(new GridMap(e.bloqueos(), true));
  }

  private Camino camino(Nodo a, Nodo b, LocalDateTime t, Vehiculo v) {
    var q = new Consulta(a, b, t, par.velocidades().get(v.tipo()));
    return caminos.computeIfAbsent(q, k -> finder.buscar(a, b, t, q.velocidad()));
  }

  public ResultadoRuta calcular(Ruta r) {
    var v = estado.vehiculos().stream().filter(x -> x.codigo().equals(r.vehiculo())).findFirst().orElseThrow();
    var origen = estado.almacenes().stream().filter(x -> x.id().equals(r.almacenOrigen())).findFirst().orElseThrow();
    LocalDateTime salidaTemprana = estado.instante().isAfter(v.disponibleDesde()) ? estado.instante() : v.disponibleDesde();
    if (r.partes().isEmpty())
      return new ResultadoRuta(r, salidaTemprana, salidaTemprana, origen.id(), List.of(), List.of(), null, null, 0, 0, List.of());
    if (r.carga() > v.tipo().capacidad())
      return error(r, salidaTemprana, "capacidad");
    if (!v.disponible())
      return error(r, salidaTemprana, "vehiculo indisponible");
    var grupos = new ArrayList<List<PartePedido>>();
    for (var parte : r.partes()) {
      if (grupos.isEmpty() || !grupos.get(grupos.size() - 1).get(0).pedido().id().equals(parte.pedido().id()))
        grupos.add(new ArrayList<>());
      grupos.get(grupos.size() - 1).add(parte);
    }
    LocalDateTime limite = r.partes().stream().map(p -> p.pedido().deadline()).min(LocalDateTime::compareTo).orElseThrow();
    ResultadoRuta fallo = null;
    LocalDateTime salida = salidaTemprana;
    // La primera alternativa conserva la salida mas temprana. Si no cabe, cada
    // alternativa posterior empieza exactamente al abrir el siguiente turno.
    while (!salida.isAfter(limite)) {
      ResultadoRuta candidata = calcularEnTurno(r, v, origen, salida, grupos);
      if (candidata.factible()) return candidata;
      fallo = candidata;
      if (r.enCurso()) break;
      salida = inicioTurno(salida).plusMinutes(par.turnoMinutos());
    }
    return fallo != null ? fallo : error(r, salidaTemprana, "sin turno antes del plazo");
  }

  private ResultadoRuta calcularEnTurno(Ruta r, Vehiculo v, Almacen origen, LocalDateTime salida,
      List<List<PartePedido>> grupos) {
    LocalDateTime turno = inicioTurno(salida);
    LocalDateTime finTurno = turno.plusMinutes(par.turnoMinutos());
    // descansoRealizado describe solo el turno vigente en el snapshot. Un turno
    // futuro comienza con la pausa aun pendiente.
    boolean descansoHecho = estado.descansoRealizado().contains(v.codigo()) && !turno.isAfter(estado.instante());
    if (salida.plusMinutes((long) grupos.size() * par.servicioMinutos() + (descansoHecho ? 0 : par.descansoMinutos()))
        .isAfter(finTurno))
      return error(r, salida, "servicio y descanso exceden turno");
    ResultadoRuta mejor = null, fallo = null;
    for (int pausa = descansoHecho ? -2 : -1; pausa <= (descansoHecho ? -2 : grupos.size()); pausa++) {
      ResultadoRuta candidata = simular(r, v, origen, salida, turno, grupos, pausa);
      if (candidata.factible() && (mejor == null || sumaFinales(candidata) < sumaFinales(mejor) ||
          (sumaFinales(candidata) == sumaFinales(mejor) && candidata.distanciaKm() < mejor.distanciaKm())))
        mejor = candidata;
      fallo = candidata;
    }
    return mejor != null ? mejor : fallo;
  }

  private LocalDateTime inicioTurno(LocalDateTime instante) {
    int minutoDia = instante.getHour() * 60 + instante.getMinute();
    return instante.toLocalDate().atStartOfDay().plusMinutes(par.inicioTurnoMinuto() +
        Math.floorDiv(minutoDia - par.inicioTurnoMinuto(), par.turnoMinutos()) * par.turnoMinutos());
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
      if (di.isAfter(bandaFin) || df.isAfter(turno.plusMinutes(par.turnoMinutos())))
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
        if (di.isAfter(bandaFin) || df.isAfter(turno.plusMinutes(par.turnoMinutos())))
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

  private long sumaFinales(ResultadoRuta ruta) {
    var finales = new HashMap<String, LocalDateTime>();
    for (var parada : ruta.paradas()) for (var parte : parada.partes())
      finales.merge(parte.pedido().id(), parada.finServicio(), (a,b) -> a.isAfter(b) ? a : b);
    return finales.values().stream().mapToLong(t -> Duration.between(estado.instante(),t).toMillis()).sum();
  }

  private ResultadoRuta error(Ruta r, LocalDateTime t, String error) {
    return new ResultadoRuta(r, t, t, r.almacenOrigen(), List.of(), List.of(), null, null, 0, 0,
        List.of(r.vehiculo() + ": " + error));
  }
}
