package pe.pucp.paqrap;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.datos.DatasetLoader;
import pe.pucp.paqrap.estricto.servicios.*;
import pe.pucp.paqrap.tabu.*;
import pe.pucp.paqrap.alns.estricto.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.security.*;
import java.util.*;
/** Experimento sobre un snapshot comun, sin simulacion de reloj. Solo JDK. */
public final class CompararAlgoritmos {
 public static void main(String[] args) throws Exception {
  if(args.length<5 || args.length>8)throw new IllegalArgumentException(
   "Uso: CompararAlgoritmos ventas.AAAAMM.txt bloqueo.AAMM.txt mantenimiento.txt instante-ISO directorio-salida [iteraciones] [semillas-separadas-por-coma] [presupuesto-ms]");
  var ventas=Path.of(args[0]);var bloqueos=Path.of(args[1]);var mantenimiento=Path.of(args[2]);
  var instante=LocalDateTime.parse(args[3]);var salida=Path.of(args[4]);
  int iteraciones=args.length>5?Integer.parseInt(args[5]):20;
  String semillas=args.length>6?args[6]:"20262,20263,20264";
  long presupuesto=args.length>7?Long.parseLong(args[7]):0;
  if(iteraciones<0 || presupuesto<0)throw new IllegalArgumentException("Presupuestos negativos");
  if(Files.exists(salida))try(var archivos=Files.list(salida)) {
   if(archivos.findAny().isPresent())throw new IllegalArgumentException("Usar un directorio de salida vacio o nuevo");
  }
  var carga=new DatasetLoader().cargar(ventas,bloqueos,mantenimiento,instante,24,400);
  var parametros=ParametrosOperacion.porDefecto();
  Files.createDirectories(salida);
  var csv=new StringBuilder("algoritmo,semilla,iteraciones,candidatos,inserciones_iniciales,Ta_ms,objetivo,costo_soles,km,tiempo_rutas_min,pedidos_totales,pedidos_completos,paquetes_pendientes,cumplimiento_pedidos,cumplimiento_paquetes,vehiculos,utilizacion,factible,completa,parada\n");
  var metadata=new StringBuilder("# Experimento estricto\n\nInstante: "+instante+"\n\n");
  for(var archivo:List.of(ventas,bloqueos,mantenimiento))metadata.append("- ").append(archivo.getFileName()).append(" SHA-256: ")
   .append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivo)))).append("\n");
  metadata.append("\nCarga: ").append(carga.pedidosLeidos()).append(" pedidos; futuros excluidos: ").append(carga.futurosExcluidos())
   .append("; fuera del horizonte: ").append(carga.fueraHorizonte()).append("; fuera del limite: ").append(carga.fueraLimite())
   .append("; considerados: ").append(carga.estado().pedidos().size()).append("; bloqueos: ").append(carga.bloqueosLeidos())
   .append("; mantenimientos: ").append(carga.estado().mantenimientos().size()).append(".\n\n")
   .append("Parametros: ").append(parametros).append("\n\n")
   .append("TS: tenencia 7, 400 candidatos/iteracion, estancamiento=").append(Math.max(1,iteraciones))
   .append(". ALNS: destruccion maxima 4 partes, segmento 5, reaccion 0.7, aceptacion 0.05, estancamiento=")
   .append(Math.max(1,iteraciones)).append(". Presupuesto por motor (ms, 0=sin reloj): ").append(presupuesto)
   .append(". Semillas: ").append(semillas).append(".\n\n")
   .append("Estado inmutable compartido; constructor y evaluador identicos. Cantidades pendientes conservadas.\n")
   .append("Una ruta por unidad/ciclo; pedidos anteriores pendientes sin despachos previos simulados.\n")
   .append("No es simulacion mensual ni evidencia de optimalidad. Igual numero de iteraciones no implica igual trabajo.\n");
  metadata.append("\nJava: ").append(System.getProperty("java.version"))
   .append("; SO: ").append(System.getProperty("os.name"))
   .append("; procesadores: ").append(Runtime.getRuntime().availableProcessors())
   .append("\nCalentamiento: 2 iteraciones por motor, excluidas del reporte. Orden alternado entre semillas.\n")
   .append("Reloj: incluye constructor; limite cooperativo, puede excederse al terminar una operacion.\n")
   .append("Candidatos TS cuenta vecinos; ALNS cuenta evaluaciones de reparacion. No comparar ese contador como trabajo identico.\n");
  var inicialEv=new EvaluadorFactibilidad(carga.estado(),parametros);
  var inicial=GeneradorSolucionInicial.generar(inicialEv);
  double objetivoInicial=inicialEv.evaluar(inicial).objetivo();
  metadata.append("Objetivo inicial comun: ").append(objetivoInicial).append("\n");
  new TabuSearchPlanner(new ConfiguracionTabu(2,7,2,400,0,20262)).planificar(carga.estado(),parametros);
  new ALNSPlanner(new ConfiguracionALNS(2,2,4,5,.7,.05,0,20262)).planificar(carga.estado(),parametros);
  int repeticion=0;
  for(String semilla:semillas.split(",")) {
   long seed=Long.parseLong(semilla.trim());
   List<PlanificadorEstricto> motores=new ArrayList<>(List.of(
    new TabuSearchPlanner(new ConfiguracionTabu(iteraciones,7,Math.max(1,iteraciones),400,presupuesto,seed)),
    new ALNSPlanner(new ConfiguracionALNS(iteraciones,Math.max(1,iteraciones),4,5,.7,.05,presupuesto,seed))));
   if(repeticion++%2==1)Collections.reverse(motores);
   for(var motor:motores) {
    var r=motor.planificar(carga.estado(),parametros);var m=r.metricas();
    var audit=new EvaluadorFactibilidad(carga.estado(),parametros).evaluar(r.solucion());
    if(!audit.factible() || Math.abs(audit.objetivo()-m.objetivo())>1e-6)throw new AssertionError("Auditoria final");
    if(audit.objetivo()>objetivoInicial+1e-6)throw new AssertionError("Resultado peor que inicial comun");
    csv.append(String.format(Locale.ROOT,"%s,%d,%d,%d,%d,%.3f,%.2f,%.2f,%.2f,%.3f,%d,%d,%d,%.6f,%.6f,%d,%.6f,%s,%s,%s%n",
     r.algoritmo(),seed,m.iteraciones(),m.candidatosEvaluados(),m.insercionesIniciales(),m.taMs(),m.objetivo(),
     m.costoOperacion(),m.distanciaKm(),m.tiempoRutasMinutos(),m.pedidosTotales(),m.pedidosCompletos(),
     m.paquetesPendientes(),m.cumplimientoPedidos(),m.cumplimientoPaquetes(),m.vehiculosUsados(),
     m.utilizacionCapacidad(),audit.factible(),audit.completa(),m.parada()));
    var detalle=new StringBuilder("# "+r.algoritmo()+" / semilla "+seed+"\n\n");
    detalle.append("Metricas: ").append(m).append("\n\n");
    for(var ruta:r.evaluacion().rutas())if(!ruta.ruta().partes().isEmpty()) {
     detalle.append("## ").append(ruta.ruta().vehiculo()).append("\n\nOrigen: ").append(ruta.ruta().almacenOrigen())
      .append("; retorno: ").append(ruta.almacenRetorno()).append("; salida: ").append(ruta.salida())
      .append("; fin: ").append(ruta.fin()).append("; descanso: ").append(ruta.descansoInicio()).append(" / ")
      .append(ruta.descansoFin()).append("\n\n");
     for(var parada:ruta.paradas())detalle.append("- ").append(parada).append("\n");
     detalle.append("\nCaminos (incluyen horas por arco):\n\n");
     for(var camino:ruta.caminos())detalle.append("- ").append(camino).append("\n");
    }
    detalle.append("\n## Partes pendientes\n\n");
    for(var parte:r.solucion().pendientes())detalle.append("- ").append(parte).append("\n");
    Files.writeString(salida.resolve(r.algoritmo()+"-"+seed+".md"),detalle,StandardCharsets.UTF_8);
    System.out.printf(Locale.ROOT,"%s seed=%d: pedidos=%d/%d; paquetes pendientes=%d; km=%.0f; Ta=%.1f ms; factible=%s; completa=%s%n",
     r.algoritmo(),seed,m.pedidosCompletos(),m.pedidosTotales(),m.paquetesPendientes(),m.distanciaKm(),m.taMs(),audit.factible(),audit.completa());
   }
  }
  Files.writeString(salida.resolve("metricas.csv"),csv,StandardCharsets.UTF_8);
  Files.writeString(salida.resolve("README.md"),metadata,StandardCharsets.UTF_8);
 }
}
