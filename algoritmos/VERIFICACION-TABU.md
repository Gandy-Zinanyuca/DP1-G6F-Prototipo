# Verificación de Tabu Search frente al cuadro inicial

La implementación nueva es **TS-estricto**, con ALNS-estricto como comparador.
No utiliza el evaluador de tardanza blanda del ALNS histórico.

| Aspecto | Implementación y evidencia |
|---|---|
| Entrada de datos | PlanificadorEstricto.planificar(EstadoOperacion, ParametrosOperacion); sin acceso a archivos desde los motores |
| EstadoOperacion | Instante java.time, pedidos pendientes, posición/estado/disponibilidad de vehículos, almacenes/stock, bloqueos, averías, mantenimientos, rutas en curso y descanso realizado |
| Carga | DatasetLoader + PedidoParser/BloqueoParser/ParserSupport; mantenimiento con intervalo de día completo; errores con archivo/línea |
| Parámetros comunes | ParametrosOperacion inmutable; ambos motores reciben el mismo objeto |
| Configuración propia | ConfiguracionTabu y ConfiguracionALNS separadas, sin Sa/Sc/K |
| Solución inicial | GeneradorSolucionInicial común, inserciones estrictas; deja partes pendientes si no encuentra una colocación válida |
| Planificación | AssignmentNeighborhood: pendiente→unidad, transferencia entre unidades, retiro a pendientes |
| Ruteo | RoutingNeighborhood: swap y relocate intra-ruta |
| Planificación ↔ ruteo | Se ejecutan ambas fases con orden alternado y presupuesto separado |
| Pedidos parciales | GestorCapacidad conserva cantidades y crea PartePedido; pueden ir a distintos vehículos |
| Turnos | Retorno dentro del turno de salida; 07/15/23 predeterminado y ancla/duración configurables |
| Descanso | Duración y banda paramétricas; horarios explícitos y sin actividad simultánea |
| Retorno | Dijkstra temporal desde última parada hacia cada almacén; selección por llegada mínima |
| Auxilio | Partes de rutas invalidadas se liberan y pueden reasignarse a otras unidades; no es transbordo físico |
| Averías | Intervalos arbitrarios; GestorDisponibilidad rechaza solapamientos en toda la ruta |
| Mantenimiento | Igual validación por intervalos, incluyendo un inicio posterior a la salida |
| Bloqueos | GridMap y PathFinder temporal; valida cruce completo de cada arista |
| Plazos | Solo 4/8/12/18/36 h; restricción dura. Por defecto, fin de servicio antes del límite |
| Camino físico | Camino y PasoCamino con nodos y tiempos, compartidos por ambos motores |
| Factibilidad común | EvaluadorFactibilidad + CalculadorRuta + gestores; misma instancia lógica por ejecución, caches aislados |
| Control TS | TabuList, TabuMove inverso, expiración, aspiración y mejor solución global |
| Clases comunes | Records en comun/estricto/modelo. Cliente representado por clienteId de Pedido; objetivo dentro del evaluador común |
| Servicios comunes | Cargador, mapa, caminos, cálculo de ruta, capacidad, disponibilidad, evaluación y constructor |
| Métricas | Ta, objetivo, costo, distancia, tiempo, cobertura por pedidos/paquetes, flota, utilización, iteraciones y candidatos |
| Salida | ResultadoPlanificacion con solución, horarios, caminos, asignaciones, pendientes y métricas |
| Tecnologías | Java 17 y java.time/java.util; javac/java sin dependencias, según la solicitud posterior de retirar Maven |
| Responsabilidad externa | Backend define reloj, pedidos incluidos/entregados, lotes, eventos/triggers, Sa/Sc/K y simulación |

## Qué significa cumplimiento estricto

Las restricciones duras se cumplen para **cada ruta aceptada**. Si la demanda completa
no se logra colocar, el resultado mantiene las partes restantes y marca completa=false.
No se convierte un incumplimiento en una ruta tardía ni se reporta cobertura total falsa.
Encontrar un plan completo no está garantizado por una metaheurística.

Son decisiones explícitas del modelo: una salida por unidad/ciclo, partes de tamaño
configurable, pausa en paradas, salida fijada por el snapshot/disponibilidad, y caminos
óptimos en tiempo de llegada. Estas decisiones acotan el espacio explorado; no demuestran
optimalidad ni exhaustividad de todas las planificaciones posibles.

Las rutas en curso requieren cantidades **aún no entregadas**, posición actual, inventario
posterior al despacho y bandera de descanso consistentes. La transferencia física de
carga averiada, la evolución de inventarios y el movimiento real de vehículos pertenecen
al simulador/backend. La reasignación nueva reserva stock de su almacén de despacho.

El formato de mantenimiento recibido se interpreta literalmente; no se extrapolan
fechas recurrentes. Un snapshot puede recibir intervalos arbitrarios mediante la API.

## Pruebas y evidencia

- Compilación javac --release 17 y tres regresiones históricas aprobadas.
- 15 grupos de regresiones estrictas aprobados en RestriccionesEstrictasTest.
- Dos snapshots reales × tres semillas × dos algoritmos: 12 ejecuciones auditadas.
- Cero errores de factibilidad de rutas en las 12 salidas; todas conservan partes.
- Septiembre: 29/36 pedidos completos, 27 paquetes pendientes.
- Octubre: 26/36 pedidos completos, 46 paquetes pendientes.
- Las 12 salidas son incompletas; no se ha probado una simulación mensual ni
  significancia estadística entre algoritmos.

Consultar [diseño](tabu/DISENO-ALGORITMOS.md), [ejecución](tabu/README.md) y
[resultados](experimentacion/COMPARACION-ESTRICTA.md).
