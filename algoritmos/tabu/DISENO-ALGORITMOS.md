# PaqRap — Componente planificador: diseño de Tabu Search

**Alcance:** búsqueda tabú para asignación y ruteo, con factibilidad estricta y contrato
común para experimentación con ALNS. Java 17, sin Maven.
La versión histórica de ALNS conserva otro contrato y no se mezcla en esta comparación.

## 1. Formulación del problema

Flota heterogénea, múltiples almacenes, pedidos con plazos duros, entregas parciales,
bloqueos temporales, turnos, descanso y disponibilidad. Cada ciclo produce como máximo
una ruta nueva por unidad, o conserva el remanente de una ruta en curso.

Valores predeterminados conservados del repositorio:

| Tipo | Capacidad | Velocidad | Tarifa por km |
|---|---:|---:|---:|
| TA | 24 | 40 km/h | S/ 8 |
| TM | 8 | 25 km/h | S/ 6 |
| TB | 4 | 12 km/h | S/ 3 |

Mapa 71 × 51 nodos de 1 km. Almacenes: central (25,15), noroeste (12,38), este (55,27).
Stock inicial de intermedios 1000; central ilimitado. Las velocidades son parámetros.
No se importaron las coordenadas y velocidades diferentes presentes en el segundo ZIP.

El evaluador rechaza violaciones de capacidad, stock agregado, plazo, turno, descanso,
avería/mantenimiento o integridad. Para soluciones con rutas válidas:

```text
f(S) = suma_r [costoFijo + distancia(r) * tarifa(tipo(r))]
     + penalizacionPaquetePendiente * paquetesPendientes(S)
```

Por defecto: costo fijo 50 y penalización de paquete pendiente 1 000 000.
No hay penalización que permita aceptar tardanza: una ruta tardía se rechaza.
La penalización de pendientes es un objetivo ponderado configurable, no una demostración
de orden lexicográfico para cualquier escala. La cobertura se reporta por separado.

## 2. Estructuras de datos y contrato

```text
DatasetLoader / parsers ──> EstadoOperacion + ParametrosOperacion
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
      TabuSearchPlanner                   ALNSPlanner
             └────────────────┬────────────────┘
                              ▼
        ResultadoPlanificacion + MetricasResultado
```

`EstadoOperacion` es un record inmutable: instante LocalDateTime, pedidos pendientes,
vehículos (posición, disponible y disponibleDesde), almacenes, bloqueos, averías,
mantenimientos, rutas en curso y unidades que ya descansaron en el turno del snapshot.
Las listas y sets se copian; sus elementos operativos son records inmutables.

`Pedido.cantidad` representa lo que **falta entregar**, no el volumen histórico original.
El backend debe excluir entregas ya realizadas y pedidos futuros. `DatasetLoader` informa
cuántos pedidos deja fuera por fecha, horizonte o máximo de pedidos.

`PartePedido` identifica cantidad y pedido padre. El generador divide por tamaño de parte
(predeterminado 4, acotado por la menor capacidad de la flota); preserva exactamente la suma.
Partes ya presentes en rutas en curso conservan su identidad. Los vecindarios no vuelven
a fraccionarlas. La discretización limita el espacio de búsqueda: no prueba toda partición
posible. Partes consecutivas del mismo pedido comparten un servicio; en unidades distintas
cada visita tiene servicio propio.

`Ruta` contiene código de vehículo, almacén de origen, partes ordenadas y bandera enCurso.
`Solucion` contiene rutas y partes pendientes. Los vecinos crean nuevas listas; la mejor
solución nunca es modificada por un vecino.

El cliente se representa por `Pedido.clienteId`; no necesita una entidad mutable separada.
`Nodo`, `Almacen`, `Parada`, `Averia` y `Mantenimiento` completan el dominio.

## 3. Servicios comunes y restricciones

| Servicio | Reglas |
|---|---|
| DatasetLoader / parsers | Período coherente y errores con archivo/línea; nunca omite silenciosamente |
| GestorCapacidad | Partes estables, cantidades positivas y conservación del pendiente |
| GestorDisponibilidad | Intervalos semiabiertos de mantenimiento/avería y disponibilidad |
| GridMap / PathFinder | Caminos temporales con espera a reapertura; ningún cruce solapa bloqueo |
| CalculadorRuta | Traslado a almacén, servicios, descanso y retorno óptimo en tiempo |
| EvaluadorFactibilidad | Integridad, stock agregado, costos y validación común |
| GeneradorSolucionInicial | Inserción por plazo probando almacén, unidad y posición |
| Resultados | Horarios, asignaciones, costos y métricas idénticas |

### 3.1 Plazos y turnos

Solo 4/8/12/18/36 horas. Por defecto el **fin del servicio** debe estar dentro del plazo.
`plazoIncluyeServicio=false` cambia la convención a llegada; el límite sigue siendo duro
y debe ser igual para ambos algoritmos.

Turnos de 480 minutos anclados a las 07:00: 07–15, 15–23 y 23–07.
Duración y ancla son parámetros; la duración debe dividir 1440.
La ruta completa, traslado inicial y retorno incluidos, termina dentro del turno de salida.
No se optimiza aquí el momento de salida: es el máximo entre el snapshot y disponibleDesde.

### 3.2 Descanso

Duración 60 minutos, íntegramente dentro de [inicioTurno+60, inicioTurno+420].
Se prueban pausas antes del traslado a almacén y en las paradas, incluyendo después de la
última entrega. No se conduce ni atiende durante la pausa. Si el backend informa descanso
ya realizado en ese turno, no se repite. Una salida posterior al turno del snapshot
necesita su propio descanso.

La pausa se sitúa en lugares de parada, no en mitad de un arco. Es una política factible,
no un optimizador continuo de todos los posibles instantes/lugares de descanso.

### 3.3 Caminos y retorno

Se reutilizó Dijkstra temporal del segundo ZIP. La duración por arco se redondea hacia
arriba al nanosegundo. Los bloqueos cierran aristas bidireccionales durante [inicio,fin).
Se puede esperar a su reapertura; cada cruce completo debe estar libre.
Se minimiza hora de llegada; se usa distancia como desempate local determinista.
Se evalúa el regreso hacia cada almacén y se elige llegada mínima, sin copiar el camino de ida.
No se afirma optimalidad global del conjunto de rutas ni distancia mínima entre todos
los caminos temporales de igual llegada.

### 3.4 Averías, mantenimiento y rutas en curso

Se verifica solapamiento con **todo el intervalo de ruta**, no solo con la salida.
El TXT de mantenimiento crea intervalos de un día; la API permite intervalos arbitrarios.
No se inventa recurrencia bimensual a partir de las fechas recibidas.

Rutas en curso válidas se conservan como comprometidas y no consumen nuevamente stock:
sus paquetes ya salieron. Su recorrido pendiente empieza en la posición actual del vehículo.
Rutas invalidadas liberan las partes para reasignación con otros vehículos disponibles;
las nuevas salidas abastecidas desde un almacén sí reservan stock. Esto modela reenviar
mercadería pendiente, no un transbordo físico ni recuperación automática de carga averiada.
El backend conserva la contabilidad física de esa carga y entrega un snapshot coherente.

## 4. Solución inicial

Ordenar partes pendientes por plazo e ID. Por cada parte, probar vehículo, almacén
(si la ruta está vacía) y todas las posiciones. Elegir la inserción factible de menor
objetivo. Si ninguna sirve, conservar la parte en pendientes y continuar con las demás.
Se conserva el mismo procedimiento para TS y ALNS.

Un snapshot imposible no se transforma artificialmente en una solución completa:
el resultado informa cobertura incompleta sin rutas inválidas.

## 5. Vecindarios de TS

**Asignación:** pendiente → ruta; ruta → otra ruta; ruta → pendientes.
El retiro permite liberar capacidad antes de insertar pedidos más útiles en pasos posteriores.
Al abrir una ruta se prueban todos los almacenes.

**Ruteo:** swap de dos partes; relocate de una parte dentro de la misma ruta.
Ambos se generan sin mutar la solución actual.

Las fases se alternan en orden en cada iteración y reciben la mitad del presupuesto
de candidatos cada una. Se barajan rutas/partes con semilla fija para que las primeras
rutas no monopolicen siempre el presupuesto. Las rutas comprometidas se excluyen.

## 6. Memoria tabú y aspiración

La lista almacena expiraciones por atributo del movimiento inverso.

- Transferencia: prohíbe que la parte vuelva al vehículo de origen, independientemente
  de la posición o de una unidad intermedia.
- Swap: pareja de partes normalizada + vehículo.
- Relocate: parte + vehículo + posición original.

Registrado en iteración i con tenencia t: prohibido en i+1,...,i+t; libre en i+t+1.
Aspiración permite el movimiento tabú solo si **es factible** y mejora estrictamente
el mejor objetivo global. TS puede empeorar el objetivo actual para explorar.

## 7. Pseudocódigo

```text
evaluador ← común(estado, parámetros)
actual ← constructor común(evaluador)
mejor ← actual
listaTabu ← vacía
mientras no se alcance iteraciones, tiempo o estancamiento:
    expirar atributos antiguos
    generar asignación y ruteo con presupuesto separado y orden alternado
    evaluar cada candidato con el evaluador común
    descartar violaciones duras
    descartar tabú salvo aspiración
    elegir el admisible de menor objetivo
    si no existe: detener
    registrar inverso del movimiento elegido
    actual ← elegido
    si actual mejora mejor: mejor ← actual
auditar mejor
devolver solución + horarios + caminos + pendientes + métricas
```

## 8. Parámetros y costo computacional

| ConfiguracionTabu | Predeterminado |
|---|---:|
| maxIteraciones | 100 |
| tenenciaTabu | 7 |
| sinMejoraMax | 40 |
| candidatosPorIteracion | 1000 |
| presupuestoMs | 0 (desactivado) |
| semilla | 20262 |

En el CLI comparativo se usan 20 iteraciones, 400 candidatos/iteración y estancamiento
igual al máximo de iteraciones. Con número impar de candidatos se usan 2×floor(n/2).
El presupuesto de tiempo es una parada entre evaluaciones, no un límite de tiempo duro:
la construcción inicial y una evaluación individual pueden sobrepasarlo.

Sin truncamiento, transferencias requieren aproximadamente O(P·V·L) vecinos y ruteo
O(V·L²), donde P es número de partes, V vehículos y L longitud de ruta.
Cada evaluación agrega restricciones globales y recalcula rutas no cacheadas.
Cada camino temporal usa Dijkstra sobre 3621 nodos. Los caches se acotan por ejecución
(1500 rutas y 4000 consultas de camino); no se comparten entre algoritmos ni snapshots.

## 9. Métricas y experimentación

Ta mide desde antes de construir la solución inicial hasta generar el resultado.
Excluye lectura de archivos, escritura de reportes y auditoría externa posterior.
Tiempo de rutas es suma de duraciones, incluye espera y descanso; no es makespan.

Candidatos evaluados cuenta llamadas al evaluador durante búsqueda (incluidas inserciones
de reparación ALNS), sin inicialización ni auditoría final. Se reportan aparte las
inserciones iniciales. No se interpreta una iteración TS como equivalente en trabajo a una ALNS.

Cumplimiento de pedidos: pedidos con todas sus partes asignadas válidamente / total.
Cumplimiento de paquetes: cantidades asignadas / cantidades pendientes del snapshot.
Utilización: carga asignada / suma de capacidades de vehículos usados.

Para reproducibilidad fijar snapshot, parámetros, semilla e iteraciones y desactivar
parada por tiempo. Los tiempos observados pueden variar por JVM/JIT y equipo.

## 10. Reutilización y validación

De `planificador-tabu.zip`: records base, parsers estrictos, GridMap, PathFinder,
TabuMove, TabuList y esquema de selección/vecindarios. Se corrigió la selección de pedidos
futuros y se añadieron almacenes, partes, turnos, descanso, averías y replanificación.

De `dp1-ruteo-tabu-search_2.zip`: esquema de inserción desde pendientes, retiro/transferencia,
control por presupuesto y consideración de turno/descanso. Se adaptaron a objetos inmutables,
caminos temporales y un único evaluador. No se copió el simulador ni su función objetivo.

De ALNS del repositorio: SelectorAdaptativo y CriterioAceptacion. ALNS-estricto agrega
destrucción aleatoria/relacionada espacialmente y reparación por plazo/orden aleatorio
sobre el mismo evaluador que TS. No se afirma que reproduzca la cartera histórica de seis operadores.

15 grupos de pruebas cubren expiración, inversión, aspiración, capacidad/división,
plazo duro, turno, descanso, disponibilidad, inventario agregado, caminos temporales,
integridad, vecindarios, auxilio y reproducibilidad. Además se ejecutó la comparación
con dos snapshots reales y tres semillas: [resultados](../experimentacion/COMPARACION-ESTRICTA.md).
