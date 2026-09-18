# Experimento estricto

Instante: 2026-09-01T08:00

- ventas.202609.txt SHA-256: 5c6bf9c1c17ab54a227a34ab502d43ef1d864a40647d7df3b8ebe9ae8b18fcca
- bloqueo.2609.txt SHA-256: 3482bfeb5fe837a69d7ddfc670634cd3cc7969c9d9c710fdd467de3373d01a9d
- mant.preventivo.09.10.txt SHA-256: 72589c0946614a8e2cb0740eedbdbe96b863953458d3c7e45c75ac41c8d8a8b0

Carga: 5000 pedidos; futuros excluidos: 4943; fuera del horizonte: 21; fuera del limite: 0; considerados: 36; bloqueos: 558; mantenimientos: 37.

Parametros: ParametrosOperacion[servicioMinutos=60, plazoIncluyeServicio=true, turnoMinutos=480, inicioTurnoMinuto=420, descansoDesde=60, descansoHasta=420, descansoMinutos=60, tamanioParte=4, costoFijoVehiculo=50.0, penalizacionPaquetePendiente=1000000.0, velocidades={TB=12.0, TM=25.0, TA=40.0}]

TS: tenencia 7, 400 candidatos/iteracion, estancamiento=20. ALNS: destruccion maxima 4 partes, segmento 5, reaccion 0.7, aceptacion 0.05, estancamiento=20. Presupuesto por motor (ms, 0=sin reloj): 0. Semillas: 20262,20263,20264.

Estado inmutable compartido; constructor y evaluador identicos. Cantidades pendientes conservadas.
Una ruta por unidad/ciclo; pedidos anteriores pendientes sin despachos previos simulados.
No es simulacion mensual ni evidencia de optimalidad. Igual numero de iteraciones no implica igual trabajo.

Java: 21.0.7; SO: Windows 11; procesadores: 12
Calentamiento: 2 iteraciones por motor, excluidas del reporte. Orden alternado entre semillas.
Reloj: incluye constructor; limite cooperativo, puede excederse al terminar una operacion.
Candidatos TS cuenta vecinos; ALNS cuenta evaluaciones de reparacion. No comparar ese contador como trabajo identico.
Objetivo inicial comun: 2.7010112E7
