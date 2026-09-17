# Verificación de ALNS frente al contrato propuesto

Revisión del código y reorganización: 16 de septiembre de 2026.
Diagnóstico del ALNS histórico previo a la integración de TS. La nueva variante ALNS-estricto y TS-estricto comparten el contrato corregido; véase [VERIFICACION-TABU.md](VERIFICACION-TABU.md). Las brechas de esta tabla se refieren exclusivamente al motor histórico.
Los nombres de archivos siguientes se refieren a paquetes bajo pe.pucp.paqrap.

| Aspecto solicitado | Estado y evidencia |
|---|---|
| Identidad ALNS | Implementado: ALNS.resolver destruye, repara, acepta por temperatura y adapta pesos con SelectorAdaptativo. |
| Entrada EstadoOperacion | Parcial: Planificador recibe ContextoPlanificacion y Solucion previa. No existe EstadoOperacion. |
| Contenido del estado | Contexto contiene Instancia, minuto, pedidos filtrados y unidades asignables; posiciones/estados viven en Vehiculo, bloqueos en MapaUrbano, mantenimiento en Instancia. No reúne lista de averías y rutas en curso en un snapshot independiente. |
| DatasetLoader / parsers | Equivalente parcial: Instancia.construir llama CargadorVentas, CargadorBloqueos y CargadorMantenimiento. Motor separado de archivos; Instancia todavía mezcla datos y fábrica de carga. |
| Parámetros comunes | ParametrosPlanificador compartido, pero incluye cadencia y semáforos propios de simulación. No equivale por completo a ParametrosOperacion. |
| Configuración ALNS | ParametrosALNS: iteraciones, tiempo, semilla, destrucción, reacción, temperatura y recalentamiento. |
| Solución inicial común | ConstructorInicial trasladado a comun/servicios; usa inserción golosa por criticidad. Segunda pasada admite tardanza: no garantiza factibilidad estricta. |
| Asignación y ruteo | EvaluadorInsercion prueba vehículo, almacén y posición; operadores destruyen y reparan secuencias. |
| Pedidos parciales | No implementado: Pedido tiene cantidad única y Solucion asocia el pedido a una unidad. EvaluadorInsercion rechaza cantidad mayor a capacidad. |
| Turnos | Parcial: Ruta.recalcular verifica retorno contra fin del turno solo si limitarRutaAlTurno=true; valor predeterminado false. |
| Descanso | Parcial: Ruta ubica una pausa; Turnos fija duración y separación mediante constantes, no una banda paramétrica. No equivale a validar descansos de todos los turnos atravesados. |
| Retorno | Implementado sobre el mapa del ciclo: calcula distancia desde última entrega a almacén cercano; no reutiliza obligatoriamente el camino de ida. |
| Auxilio / averías | Parcial: herencia de plan y RemocionPorAveria retiran/reasignan pedidos de unidades no asignables. No modela explícitamente encuentro físico y transferencia de carga entre vehículos. |
| Mantenimiento | Parcial: Contexto filtra por día mediante Instancia.unidadesEnMantenimiento; no comprueba solapamiento de toda la ruta con un intervalo arbitrario. |
| Bloqueos | Parcial: MapaUrbano fija bloqueos al minuto del ciclo; no evalúa su vigencia a la hora de recorrer cada arco futuro. |
| Plazos duros 4/8/12/18/36 h | No cumple como restricción dura: Ruta acumula tardanza y colocarRezagados permite reinserciones no admisibles. Se mide llegada, no fin del servicio. |
| GridMap + PathFinder | Equivalente integrado: MapaUrbano calcula distancias y caminos mínimos con BFS; no son dos servicios separados. |
| Factibilidad común | Parcial: lógica repartida entre Ruta.recalcular, EvaluadorInsercion, Contexto y Solucion; ya reside en comun, pero no hay EvaluadorFactibilidad único. |
| Clases comunes | Pedido, Vehiculo, TipoVehiculo, Almacen, Bloqueo, Averia, Mantenimiento, Ruta y Solucion existen. Coordenada equivale a nodo; no hay Cliente o Parada independientes. |
| Servicios comunes | Cargadores y mapa existen; cálculo de ruta, capacidad, disponibilidad y objetivo están integrados en las clases mencionadas. Constructor y evaluación de inserción extraídos de ALNS. |
| Control de búsqueda | ALNS implementado. TabuList, TabuMove, aspiración y vecindarios TS pendientes. |
| Métricas comparables | Parcial: Solucion expone costo, distancia, tardanza y unidades; Ruta expone horarios y utilización. ALNS.Estadisticas expone tiempo e iteraciones; faltan DTO común, candidatos evaluados y definición inequívoca de Ta/cumplimiento. |
| Salida ResultadoPlanificacion | No existe: retorna Solucion y estadísticas específicas por separado. |
| Java/Maven/JUnit | Compilación básica con JDK 17+, sin Maven ni JUnit; pruebas Java ejecutables con main. El tiempo operativo sigue siendo minutos enteros, no java.time. |
| Responsabilidad externa | La demo controla ciclos; el motor no avanza el reloj. Queda separar parámetros de simulación y consolidar entrada/salida con el backend. |

## Preparación antes de comparar TS y ALNS

1. Definir EstadoOperacion con instante y zona/base temporal, pedidos, vehículos, almacenes,
   bloqueos, averías, mantenimiento y rutas en ejecución. Acordar copias del estado mutable:
   construir dos contextos sobre la misma Instancia no crea dos snapshots independientes.
2. Separar DatasetLoader y el adaptador al contexto; mantener filtrado/priorización iguales
   para ambos motores y registrar los pedidos excluidos por horizonte y máximo por ciclo.
3. Modelar partes de pedido y cantidades entregadas/pendientes antes de implementar transferencias.
4. Centralizar factibilidad: plazos duros, retorno dentro del turno, banda de descanso,
   mantenimiento por intervalo y bloqueos dependientes del tiempo. Si se permite explorar
   soluciones inviables, verificar la solución final y reportar explícitamente la inviabilidad.
5. Incorporar ResultadoPlanificacion/MetricasResultado con idénticas definiciones:
   tiempo de búsqueda Ta, costo, distancia, duración de rutas, cumplimiento, uso de flota,
   utilización, iteraciones y candidatos. No confundir Ta con tiempo simulado.
6. Implementar TS sobre comun y ejecutar ambos desde copias equivalentes del estado,
   con el mismo constructor, restricciones y objetivo. Semilla igual no garantiza
   reproducibilidad si la parada depende del reloj; usar iteraciones fijas para regresiones.

## Validación incluida

IntegracionALNSTest usa escenarios sintéticos sin archivos externos:
conservación de pedidos, capacidad, inventario sin mutar stock, costo no peor que el
constructor y asignaciones reproducibles con semilla e iteraciones fijas.
Otra prueba caracteriza explícitamente las brechas actuales de tardanza,
pedidos indivisibles y turno no estricto, para no ocultarlas durante la migración.

La prueba histórica PruebaPlanificador permanece en experimentacion y requiere un dataset.

Se retiraron Maven y JUnit a solicitud del usuario. Las tres comprobaciones se conservan
como pruebas Java sin dependencias y se ejecutan desde los scripts de compilación.
Consulte REINTEGRAR-MAVEN.md para restaurar la construcción por módulos.

Validación posterior al retiro: compilar.bat y su lanzador de compatibilidad en alns
compilan con --release 17 y ejecutan correctamente las tres pruebas Java. El script
Bash no se ejecutó en este entorno Windows. No se ejecutó un dataset real.
