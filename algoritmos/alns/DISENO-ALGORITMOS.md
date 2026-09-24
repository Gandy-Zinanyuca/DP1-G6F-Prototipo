# PaqRap — Componente planificador: diseño del algoritmo ALNS

**Curso** 1INF54 · Proyecto de Diseño y Desarrollo de Software · Equipo 6F · 2026-2
**Referencia normativa** Informe de Selección de Algoritmos (ISA) v3.0, sección 5.2. Este
documento describe cómo la implementación sigue ese pseudocódigo y registra las pocas decisiones
que el ISA deja abiertas.

---

## 1. Problema y modelo

MDVRPTW dinámico con flota heterogénea fija: tres almacenes (central ilimitado; intermedios con
inventario), tres tipos de unidad, hora límite por pedido y bloqueos de calles con inicio y fin.

| Tipo | Capacidad | Velocidad | Costo |
|---|---|---|---|
| Auto (TA) | 24 paquetes | 40 km/h | S/ 8.00 por km |
| Moto (TM) | 8 paquetes | 25 km/h | S/ 6.00 por km |
| Bicicleta (TB) | 4 paquetes | 12 km/h | S/ 3.00 por km |

**Restricciones duras** (una sola violación hace no factible la solución; un incumplimiento de
plazo es colapso logístico):

- capacidad de la unidad **en cada viaje** y a lo sumo `maxViajesPorRuta` viajes por ruta (§4.4);
- llegada a cada destino no posterior a su hora límite; 1 hora de entrega por destinatario;
- disponibilidad de la unidad y ausencia de mantenimiento preventivo durante toda la ruta;
- caminos calculados con CAMINO_MÁS_RÁPIDO respetando los bloqueos en la hora real de cruce;
- la cantidad de cada pedido considerado cubierta exactamente, completa en una unidad o
  repartida entre varias (ver §4.3), salvo los pedidos **reprogramables** (§4.5);
- regreso a un almacén;
- *adicionales al EVALUAR del ISA*: inventario no negativo en almacenes intermedios, hora de
  alimentación obligatoria en cada turno, separada 1 h de los cambios de turno (§4.7), y
  confinamiento al turno si `limitarRutaAlTurno` está activo.

**Costo** (solo compara soluciones factibles): `Σ_r distanciaKm(r) × costoPorKm(τ(r))` más
`penalizaciónPorPaquete × paquetes reprogramados` (1 000 000 por paquete: domina cualquier
diferencia de distancia, así que solo se reprograma lo que no cabe).

**Pedidos considerados en el instante T** (ventana de consumo `Sc = Sa × K`): los pendientes —no
entregados, registrados hasta T— más los registrados en `(T, T + Sc]`. La ruta sale en
`máx(T, disponibilidad de la unidad, registro más tardío de sus pedidos)`.

---

## 2. Estructuras de datos

```
Solucion                                   Ruta
 ├── rutas          : unidad → Ruta         ├── vehiculo, almacenOrigen, secuencia   (primarios)
 ├── noAsignados    : pedidos               └── derivados: distanciaKm, llegadas, salidas de
 ├── ubicacion      : parte → unidad            tramo, retorno, alimentación, factible, motivo
 ├── consumoAlmacen : almacén → unidades
 └── errores        : resultado de EVALUAR

ParametrosALNS  (ConfiguracionALNS del ISA)
SelectorAdaptativo<T>  (peso, puntuaciónSegmento, usosSegmento por operador = OperadorALNS)
```

Las rutas se recalculan solo cuando su secuencia cambia (`asegurarCalculada`). Colecciones con
orden estable y una semilla única garantizan reproducibilidad (LE008).

### 2.1 CAMINO_MÁS_RÁPIDO (`MapaUrbano.caminoMasRapido`)

Cada calle unitaria guarda los intervalos `[inicio, fin)` de los bloqueos que la cierran (arreglo
indexado por calle). Búsqueda A* temporal: la cola se ordena por llegada + distancia Manhattan
restante × tiempo de cruce, cota consistente porque las esperas solo retrasan, así que da la misma
llegada óptima que Dijkstra explorando menos nodos. Una calle solo se cruza si el cruce completo no
se solapa con un cierre; si se solapa, la unidad espera hasta el fin del bloqueo. Atajos exactos: si
ningún bloqueo está activo durante la ventana de viaje (búsqueda binaria sobre los intervalos), o si
el camino en L no requiere esperas, la llegada Manhattan es óptima y no se ejecuta la búsqueda. Los
resultados con bloqueos se memorizan.

---

## 3. Algoritmo principal (ISA 5.2)

```
inicioReal ← nanoTime
soluciónInicial ← GENERAR_SOLUCIÓN_INICIAL ; EVALUAR
SI no es factible → retornar resultado no factible (colapso), sin iterar
actual ← mejorGlobal ← soluciónInicial ; pesos ← pesoInicial
MIENTRAS iteración < maxIteraciones
    iteración++ ; candidatosEvaluados++
    d ← SELECCIONAR_OPERADOR(destrucción) ; r ← SELECCIONAR_OPERADOR(reparación)
    grado ← DETERMINAR_GRADO_DESTRUCCIÓN(actual)
    (parcial, removidos) ← APLICAR_DESTRUCCIÓN(copia(actual), d, grado)
    candidato ← APLICAR_REPARACIÓN(parcial, removidos, r)
    SI EVALUAR(candidato) no factible → candidatosNoFactibles++ ; puntuación ← rechazo
    SINO candidatosFactibles++ ; (aceptar, puntuación) ← CRITERIO_ACEPTACIÓN
         SI aceptar: actual ← candidato
              SI costo < costo(mejorGlobal): mejorGlobal ← copia ; puntuación ← nuevoMejor
    puntuar d y r ; SI iteración mod tamañoSegmento = 0 → ACTUALIZAR_PESOS
ACTUALIZAR_PESOS ; Ta ← (nanoTime − inicioReal)/10⁶
RETORNAR mejorGlobal
```

| Pieza del ISA | Implementación |
|---|---|
| GENERAR_SOLUCIÓN_INICIAL | `ConstructorInicial`: deadline ascendente, mejor inserción factible; si no cabe completo en ninguna unidad se reparte (§4.3); si tampoco → no factible |
| DETERMINAR_GRADO_DESTRUCCIÓN | `máx(1, round(total × proporción × (1 − 0,7·ocupación)))`; la reducción por ocupación se desactiva con `destruccionAdaptativaPorOcupacion` |
| SELECCIONAR_OPERADOR | `SelectorAdaptativo.seleccionar`: ruleta con `valorAleatorio ≤ acumulado` |
| ACTUALIZAR_PESOS | `peso ← peso·(1 − r) + r·(puntuaciónSegmento/usosSegmento)` |
| CRITERIO_ACEPTACIÓN | `CriterioAceptacion`: mejora → `puntuacionMejoraActual`; `U < exp(−Δ/T)` → `puntuacionAceptacionNoMejora`; si no, rechazo |
| CALCULAR_TEMPERATURA | `T = temperaturaInicial × factorEnfriamiento^iteración` |
| EVALUAR | `Solucion.evaluar` + `Ruta.recalcular` |

---

## 4. Operadores

### 4.1 Destrucción

| Operador | Clase | Criterio |
|---|---|---|
| eliminación aleatoria | `RemocionAleatoria` | `grado` pedidos al azar |
| eliminación relacionada | `RemocionRelacionadaShaw` | semilla al azar; resto ordenado una vez por relación = distancia/120 + \|Δ deadline\|/rango + (0 misma ruta, 1 otra); se toman los más relacionados |
| eliminación por peor costo | `RemocionPeor` | repetir `grado` veces: retirar el pedido con mayor `costo(parcial) − costo(parcial sin pedido)`, recalculando |
| blocked-arc removal | `RemocionPorArcoBloqueado` | todos los pedidos cuyo tramo de llegada (CAMINO_MÁS_RÁPIDO) usa una calle bloqueada vigente en T |
| vehicle-failure removal | `RemocionPorAveria` | todos los pedidos de unidades con mantenimiento vigente en T o no disponibles (averiadas) |

Los dos operadores de dominio no usan el grado de destrucción.

### 4.2 Reparación

| Operador | Clase | Criterio |
|---|---|---|
| inserción voraz | `InsercionGolosa` | removidos por deadline ascendente; cada uno en su inserción factible de menor costo; sin inserción → no asignado |
| inserción por arrepentimiento | `InsercionPorArrepentimiento` | inserciones factibles (vehículo, posición) por costo; regret = 2.ª − 1.ª, o `arrepentimientoSinAlternativa` si hay una sola; se inserta el de mayor regret; si ninguno tiene inserción → restantes no asignados |

`EvaluadorInsercion` enumera las inserciones factibles: poda por capacidad, prueba cada posición
evaluando la ruta, y en unidades ociosas prueba cada almacén de origen con stock. El reparador por
arrepentimiento memoriza las inserciones por (pedido, unidad) e invalida solo la unidad modificada.

### 4.3 Pedidos repartidos entre unidades

Un pedido puede dividirse entre vehículos. Las rutas transportan *partes*: el pedido completo o
una fracción (`Pedido.fraccion(q)`: mismo id, destino, registro y hora límite, cantidad `q`).

```
INSERTAR_FRACCIONADO(pedido)          // solo si ninguna unidad admite el pedido completo
restante ← cantidad
MIENTRAS restante > 0
    PARA CADA unidad disponible que no lleve ya una parte del pedido
        q ← mín(restante, capacidad libre) ; mejor inserción factible de una fracción de q
    aplicar la fracción de mayor q (desempate: menor costo)
    SI no existe → deshacer lo aplicado ; fallar
    restante ← restante − q
```

- Lo usan `ConstructorInicial` y los dos reparadores como alternativa antes de marcar un pedido
  como no asignado (el de arrepentimiento reparte primero el pendiente de menor deadline).
- Tras cada destrucción, las partes removidas de un mismo pedido se fusionan
  (`Solucion.consolidar`) para reinsertarlas juntas y fraccionar solo si hace falta.
- EVALUAR verifica la cobertura por cantidad: Σ partes asignadas = cantidad pendiente del pedido.
- Se evalúa igual que la solución inicial de Búsqueda Tabú, que también fracciona como último
  recurso, para que ambos algoritmos resuelvan el mismo problema.

### 4.4 Rutas con recarga (viajes múltiples)

El curso permite que una unidad recargue en cualquier almacén con stock y siga repartiendo
(preguntas y respuestas, pregunta 10; la hoja Flota fija un mínimo de viajes diarios: auto 3,
moto 6, bicicleta 2,1). La ruta sigue siendo *(unidad, almacén de origen, secuencia)*; los viajes
se **derivan** de la secuencia:

```
carga ← 0 ; almacén ← origen
PARA CADA pedido de la secuencia
    SI carga + cantidad > capacidad
        ir al almacén más cercano con stock para el siguiente viaje ; recargar ; carga ← 0
    entregar ; carga ← carga + cantidad
regresar al almacén más cercano
```

- Los operadores no cambian: la posición en que se inserta un pedido decide también si la unidad
  sigue la ruta o vuelve antes a recargar.
- Capacidad por viaje; a lo sumo `maxViajesPorRuta` viajes (3), que acota el horizonte de la ruta
  y el costo de la búsqueda; el plan se rehace cada Sa.
- El inventario se descuenta en el almacén donde carga cada viaje; la solución suma los consumos
  de todas las rutas (restricción global).
- La hora de alimentación se ubica en cada turno que abarca la ruta (§4.7).
- Podas de la inserción que no cambian las soluciones factibles: se descarta la unidad si el
  pedido no cabe ni usando todos sus viajes, se deja de probar posiciones una vez que la parada
  previa llega —aun sin comidas— después de la hora límite del pedido, y el inventario solo se revisa en los
  almacenes intermedios que usa la ruta modificada.

### 4.5 Reprogramación de pedidos con holgura

Un pedido cuya hora límite está a más de `holguraMinimaPostergacionMin` (240 min) del instante T
puede quedar sin asignar en el plan: se atenderá en un ciclo posterior. No hace infactible la
solución, pero cada paquete reprogramado suma la penalización al costo. GENERAR_SOLUCIÓN_INICIAL
sigue igual, salvo que un pedido que no cabe y es reprogramable no detiene la construcción (sí la
detiene uno que no lo es). Un pedido que ya no es reprogramable y no cabe provoca el colapso.

### 4.6 Solución inicial con plan previo

```
heredada ← plan vigente sin lo despachado, con los pedidos nuevos insertados (voraz)
desdeCero ← GENERAR_SOLUCIÓN_INICIAL
inicial ← la factible; entre dos factibles, la de menor costo
```

La reoptimización incremental ya no puede dejar un plan infactible cuando replanificar desde cero
lo resolvería (causa de la mitad de los colapsos en la campaña con el modelo anterior).

### 4.7 Hora de alimentación (LE018)

Cada chofer toma **una hora de alimentación obligatoria por turno**, que debe empezar dentro de la
ventana `[inicio del turno + 1 h, fin del turno − 2 h]`: nunca en los extremos del turno, pero sí
en cualquier punto intermedio. El momento lo decide el planificador para cada unidad al evaluar su
ruta (`Ruta.recalcular`), así que las unidades no comen todas a la vez:

```
recorrido ← SIMULAR(ruta sin comidas)
PARA CADA turno cuya ventana abarca la ruta, en orden
    SI la unidad ya comió en ese turno, o regresa antes de que cierre la ventana → siguiente
    candidatos ← antes de salir (tiempo ocioso de la unidad), en el almacén de origen,
                 tras cada entrega; solo los que pueden empezar dentro de la ventana
    recorrido ← el mejor SIMULAR(ruta + comida en el candidato):
                menor atraso, luego menor distancia, regreso más temprano, mayor holgura
    SI no hay candidato → ruta no factible
```

- **Antes de salir**: la unidad está libre desde que quedó disponible (puede ser antes de T);
  si comió en ese tiempo ocioso, la salida no se retrasa. Si no, sale al terminar de comer.
- **Regreso a tiempo**: si la ruta termina antes de que cierre la ventana, la unidad come después
  en el almacén; la ruta siguiente de esa unidad tendrá que dejarle ese espacio.
- Rutas largas (recargas) pueden abarcar dos turnos y llevar dos comidas.
- De los puntos en que la unidad queda libre antes de que abra la ventana solo se prueba el último
  (todos esperarían a la apertura). Con 1 h por entrega, la ventana de 5 h deja pocos candidatos.
- Se prueba primero la comida antes de salir y luego del punto más tardío al más temprano; la
  búsqueda se corta en el primer candidato sin atrasos, sin más distancia y que retrasa el regreso
  a lo sumo 1 h (solo una espera absorbida podría mejorarlo). Sin este corte, Ta se multiplicaba
  por ~5.
- Al despachar, el simulador registra el turno de la última comida incluida en los viajes que
  salen (`Vehiculo.turnoDeUltimaAlimentacion`).

---

## 5. Simulación y escenario de colapso (`simulacion.Simulador`)

```
t ← día 1, 00:00 del mes inicial
REPETIR
    cargar el mes siguiente si t + Sc + 48 h lo alcanza (ventas y bloqueos desplazados)
    registrar entregas con llegada ≤ t ; liberar unidades que regresaron
    SI una entrega llegó tarde → COLAPSO
    SI cambió el día → recargar almacenes intermedios
    SI vence el plazo de un pedido sin despachar → COLAPSO
    ctx ← pedidos de la ventana (solo la cantidad no despachada) y unidades disponibles
    plan ← ALNS(ctx, planVigente)
    SI plan no es factible → COLAPSO
    despachar los viajes que salen antes de t + Sa ; planVigente ← plan
    t ← t + Sa
HASTA colapso, fin de los datos o límite de ciclos
```

- **Despacho progresivo por viaje**: un viaje despachado compromete la unidad hasta que llega al
  almacén donde termina y descuenta el inventario del almacén donde carga; los viajes siguientes
  y las demás rutas se replanifican en el ciclo siguiente partiendo del plan vigente.
- **Diagnóstico del colapso** (`DiagnosticoColapso`): demanda de la ventana frente a la capacidad
  de un viaje, si el pedido cabe solo o en un segundo viaje, la misma prueba y una replanificación
  sin bloqueos, replanificación desde cero, arrepentimiento desde vacío y mantenimientos.
- **Entregas**: un pedido pasa a entregado cuando el reloj alcanza la llegada de su última parte.
  Todas las partes conservan registro, destino y hora límite del pedido, y cada una debe llegar
  antes de esa hora.
- **Pendientes en el punto de inicio**: los pedidos registrados antes del arranque con plazo
  vigente se mantienen, incluidos los del mes anterior (y sus bloqueos vigentes). Al cambiar de
  mes, los pendientes y las rutas en curso continúan sin corte.
- **Reloj global**: minutos desde el día 1 del mes inicial; los mantenimientos se ubican por fecha
  de calendario, así que siguen aplicando al encadenar meses. Los bloqueos ya terminados se
  descartan al cargar cada mes.
- **Métricas** (`ResultadoSimulacion`): instante y causa del colapso, días simulados, ciclos,
  tiempo real de la corrida, Σ Ta con su promedio y máximo, pedidos registrados, entregados y
  fraccionados, rutas, km y costo. `--csv-resumen` agrega una fila por corrida y `--csv-ciclos`
  guarda una fila por ciclo.
- El modo `--colapso` usa `PlanificadorALNS.paraColapso()` (destrucción de 0,10) y sin límite de
  ciclos; `--iteraciones` reemplaza su número de iteraciones.

---

## 6. Parámetros (`ParametrosALNS`)

El ISA no fija valores; los siguientes son puntos de partida para la calibración.

| Parámetro | Por defecto |
|---|---|
| `saMinutos` (Sa) / `k` (K) → `scMinutos()` | 10 / 7 → 70 |
| `maxIteraciones` | 1 000 |
| `proporcionDestruccion` | 0,20 |
| `destruccionAdaptativaPorOcupacion` | sí |
| `tamanioSegmento` | 100 |
| `pesoInicial` | 1,0 |
| `factorReaccion` | 0,10 |
| `puntuacionNuevoMejor` / `MejoraActual` / `AceptacionNoMejora` / `Rechazo` | 33 / 9 / 13 / 0 |
| `arrepentimientoSinAlternativa` | 1 000 000 |
| `temperaturaInicial` / `factorEnfriamiento` | 100 / 0,995 |
| `semilla` | 20262 |

Métricas en `ALNS.Estadisticas`: candidatosEvaluados, candidatosFactibles, candidatosNoFactibles,
aceptados, rechazados, nuevasMejores, iteraciónMejor, Ta (ms), factible, errores, pesos y usos.

---

## 7. Puntos a reflejar en el ISA

1. EVALUAR debe listar el inventario de almacenes intermedios, la hora de alimentación
   obligatoria por turno (con su ubicación elegida en la evaluación, §4.7) y el turno como
   restricciones duras, y las estructuras deben incluir `Almacen`.
2. La ruta parte del almacén de origen elegido (multi-almacén) y regresa al almacén más cercano.
3. La ventana de pedidos incluye los pendientes registrados antes de T además de `(T, T+Sc]`.
4. Forma concreta de la reducción por ocupación: `proporción × (1 − 0,7·ocupación)`.
5. vehicle-failure removal considera también unidades averiadas (no solo mantenimiento).
6. Los pedidos pueden repartirse entre varias unidades (§4.3): GENERAR_SOLUCIÓN_INICIAL y
   APLICAR_REPARACIÓN fraccionan antes de declarar un pedido no asignado, y EVALUAR verifica la
   cobertura por cantidad.
7. EVALUAR de ALNS es una implementación equivalente dentro de este módulo, no la misma clase
   que la de TS (módulos Java separados).
8. Con el esquema de puntuación del ISA, una iteración en la que blocked-arc o vehicle-failure no
   remueven nada produce un candidato de igual costo que se acepta con
   `puntuacionAceptacionNoMejora`; esos operadores ganan peso sin aportar. Conviene decidir si una
   destrucción vacía debe puntuar como rechazo.
9. Rutas con recarga (§4.4), reprogramación con penalización (§4.5) y solución inicial con
   plan previo (§4.6) extienden EVALUAR y GENERAR_SOLUCIÓN_INICIAL del ISA.
