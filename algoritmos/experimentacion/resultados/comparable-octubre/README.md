# Experimento estricto

Instante: 2026-10-01T08:00

- ventas.202610.txt SHA-256: 7303a1c8c287430199e5cd8926c8f46c389dd27edad2d7e829bf71b15425099b
- bloqueo.2610.txt SHA-256: 0cbc7d3c9f933b1500e74ab17f9d79656307156daaf3994e636874bcca5565d2
- mant.preventivo.09.10.txt SHA-256: 72589c0946614a8e2cb0740eedbdbe96b863953458d3c7e45c75ac41c8d8a8b0

Carga: 5000 pedidos; futuros excluidos: 4939; fuera del horizonte: 25; fuera del limite: 0; considerados: 36; bloqueos: 622; mantenimientos: 37.

Parametros: ParametrosOperacion[servicioMinutos=60, plazoIncluyeServicio=true, turnoMinutos=480, inicioTurnoMinuto=420, descansoDesde=60, descansoHasta=420, descansoMinutos=60, tamanioParte=4, costoFijoVehiculo=50.0, penalizacionPaquetePendiente=1000000.0, velocidades={TM=25.0, TA=40.0, TB=12.0}]

TS: tenencia 7, 400 candidatos/iteracion, estancamiento=20. ALNS: destruccion maxima 4 partes, segmento 5, reaccion 0.7, aceptacion 0.05, estancamiento=20. Presupuesto por motor (ms, 0=sin reloj): 0. Semillas: 20262,20263,20264.

Estado inmutable compartido; constructor y evaluador identicos. Cantidades pendientes conservadas.
Una ruta por unidad/ciclo; pedidos anteriores pendientes sin despachos previos simulados.
No es simulacion mensual ni evidencia de optimalidad. Igual numero de iteraciones no implica igual trabajo.

Java: 21.0.7; SO: Windows 11; procesadores: 12
Calentamiento: 2 iteraciones por motor, excluidas del reporte. Orden alternado entre semillas.
Reloj: incluye constructor; limite cooperativo, puede excederse al terminar una operacion.
Candidatos TS cuenta vecinos; ALNS cuenta evaluaciones de reparacion. No comparar ese contador como trabajo identico.
Objetivo inicial comun: 4.6009046E7
