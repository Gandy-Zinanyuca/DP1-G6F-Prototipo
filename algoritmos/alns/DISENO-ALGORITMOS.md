# PaqRap — Componente planificador: diseño del algoritmo ALNS

**Curso** 1INF54 · Proyecto de Diseño y Desarrollo de Software · Equipo 6F · 2026-2
**Alcance de este documento** pseudocódigo, estructuras de datos y decisiones de diseño de la
primera de las dos soluciones algorítmicas exigidas por RNF01. La Búsqueda Tabú se implementará
sobre las mismas estructuras y la misma función objetivo, de modo que la comparación de la
experimentación numérica sea válida.

---

## 1. Formulación del problema

El problema de PaqRap es un **MDVRPTW dinámico con flota heterogénea fija**: múltiples almacenes
con inventario limitado, ventanas de tiempo duras derivadas del plazo comprometido, tres tipos de
unidad con capacidad, velocidad y costo distintos, y perturbaciones que aparecen durante la
ejecución (bloqueos de calles y averías de unidades).

**Conjuntos y datos.** Sea `P` el conjunto de pedidos del ciclo, `V` el de unidades asignables y
`A` el de almacenes. Cada pedido `p` tiene destino `dest(p)`, cantidad `q(p)`, instante de
registro `reg(p)` y hora límite `lim(p) = reg(p) + plazo(p)`, con `plazo ∈ {4, 8, 12, 18, 36}`
horas. Cada unidad `v` tiene tipo `τ(v) ∈ {auto, moto, bicicleta}` con capacidad `Q(τ)`,
velocidad `s(τ)` y tarifa `c(τ)`:

| Tipo | Capacidad | Velocidad | Costo |
|---|---|---|---|
| Auto | 24 paquetes | 40 km/h | S/ 8.00 por km |
| Moto | 8 paquetes | 25 km/h | S/ 6.00 por km |
| Bicicleta | 4 paquetes | 12 km/h | S/ 3.00 por km |

**Mapa.** Retícula de 71 × 51 nodos separados 1 km, con arcos de doble sentido. Almacén central
en (25, 15) —inventario ilimitado—; intermedios en (12, 38) y (55, 27), con 1 000 unidades de
capacidad y recarga instantánea diaria a las 23:59:59.

**Restricciones.** Capacidad por unidad; hora límite por pedido; una hora de entrega por
destinatario; una hora de alimentación por jornada, separada al menos una hora de cada cambio de
turno (07:00, 15:00, 23:00); inventario no negativo en los almacenes intermedios; retorno a un
almacén al terminar el recorrido; y arcos cerrados por los bloqueos vigentes.

**Función objetivo.** Se minimiza

```
f(S) = w_d · Σ_r [ dist(r) · c(τ(r)) ]          costo de operación
     + w_u · |{r ∈ S : r ≠ ∅}|                   costo fijo por unidad en servicio
     + w_p · |{p : llegada(p) > lim(p)}|         pedidos fuera de plazo
     + w_m · Σ_p max(0, llegada(p) − lim(p))     minutos de tardanza
     + Σ_{p ∉ S} [ w_n + w_c · criticidad(p) ]   pedidos no despachados
```

Los pesos por defecto son `w_n = 1 000 000`, `w_c = 500 000`, `w_p = 200 000`, `w_m = 10`,
`w_u = 50`, `w_d = 1`. La escala **no es arbitraria**: induce un orden lexicográfico de hecho que
refleja la definición de colapso del problema. Despachar tarde es preferible a no despachar
—la política de PaqRap no admite cancelar entregas—; no incumplir es preferible a ahorrar
kilómetros; y solo entre soluciones equivalentes en lo anterior se minimiza el costo. El término
de criticidad implementa la priorización por tiempo restante que exige LE096.

---

## 2. Estructuras de datos

### 2.1 Representación de la solución

```
Solucion
 ├── rutas          : LinkedHashMap<codigoUnidad, Ruta>
 ├── noAsignados    : LinkedHashSet<Pedido>
 ├── ubicacion      : Map<idPedido, codigoUnidad>     índice inverso, acceso O(1)
 └── consumoAlmacen : Map<idAlmacen, unidades>        acoplamiento de inventario entre rutas

Ruta
 ├── vehiculo       : Vehiculo                        dato primario
 ├── almacenOrigen  : Almacen                         dato primario
 ├── secuencia      : List<Pedido>                    dato primario
 └── (derivados)    distanciaKm, minutosLlegada[], minutoRetorno, tardanzaTotal,
                    pedidosTardios, cargaTotal, factible, almacenRetorno,
                    minutoInicioAlimentacion
```

Tres decisiones sostienen esta representación:

**Solo la secuencia es dato primario.** Tiempos, distancias y carga son derivados y se recalculan
con una única pasada hacia adelante en O(n). Los operadores de destrucción y reparación solo
insertan y remueven posiciones; nunca tienen que mantener invariantes de tiempo a mano, que es la
fuente habitual de errores en implementaciones de ruteo.

**El índice inverso hace baratos los destructores.** Remover un pedido no exige recorrer las
rutas: `desasignar(p)` localiza su ruta en O(1).

**El inventario vive en la solución, no en la ruta.** El stock de un almacén intermedio acopla
varias rutas, de modo que ninguna ruta individual puede verificarlo. `consumoAlmacen` mantiene ese
acoplamiento y permite podar inserciones inviables en O(1).

El uso de `LinkedHashMap` y `LinkedHashSet` no es cosmético: junto con una semilla fija del
generador aleatorio, el orden de iteración estable es lo que garantiza la reproducibilidad exigida
por LE008 y LE009.

### 2.2 Mapa urbano y bloqueos

La red vial se guarda como un `byte[]` de una posición por nodo (3 621 bytes en total): los cuatro
bits bajos indican qué arcos incidentes están cerrados. Comprobar si un arco es transitable cuesta
O(1).

Como todos los arcos pesan 1 km, la distancia mínima entre dos nodos se obtiene con una **BFS**
que en una sola pasada de O(V + E) ≈ 14 000 operaciones produce la distancia del origen a *todos*
los nodos. Por eso no se cachean pares origen–destino sino vectores completos por origen: los
orígenes que el planificador consulta repetidamente —almacenes, posiciones de unidades, destinos
de clientes— pagan una sola BFS por ciclo. Cuando no hay bloqueos vigentes se omite la BFS y se
devuelve la distancia Manhattan, que en una retícula sin cierres es exactamente la distancia
mínima.

Esa misma propiedad da un detector de bloqueos gratuito: **si la distancia real supera a la
Manhattan, el tramo fue desviado por un cierre**. Es la comprobación en O(1) que usa el operador
de remoción por arco bloqueado.

### 2.3 Dependencia temporal

Los bloqueos tienen intervalo de vigencia, así que la red cambia a lo largo del horizonte. El
planificador evalúa cada ciclo con la fotografía de la red vigente al inicio del ciclo
(`MapaUrbano.fijarInstante`) y vuelve a planificar cada 15 minutos simulados (LE026). La dinámica
del problema se absorbe en esa cadencia de replanificación, no dentro de la evaluación de una ruta
individual.

---

## 3. Evaluación de una ruta

```
FUNCIÓN recalcular(ruta, contexto)
  carga ← Σ q(p) para p en secuencia
  SI carga > Q(τ(v)) ENTONCES marcar infactible

  t ← max(disponibleDesde(v), minutoActual)
  [inicioTurno, finTurno] ← turno que contiene a t
  ventana ← [inicioTurno + 60, finTurno − 120]        // hora de alimentación admisible

  R₀ ← simular(ruta, sin alimentación)                 // pasada 1: O(n)
  SI ruta no alcanzable ENTONCES devolver infactible

  SI la unidad no ha comido en este turno Y R₀ solapa la ventana ENTONCES
      j ← última parada cuyo instante de disponibilidad ≤ fin de ventana
      inicioComida ← max(disponibilidad(j), inicio de ventana)
      R ← simular(ruta, comida tras la parada j)       // pasada 2: O(n)
  SINO
      R ← R₀

  SI limitarRutaAlTurno Y minutoRetorno(R) > finTurno ENTONCES marcar infactible
  copiar derivados de R a la ruta
```

**Por qué dos pasadas.** La ubicación de la hora de alimentación afecta todos los tiempos
posteriores, así que no puede decidirse sobre la marcha sin lookahead. La regla implementada es
*lo más tarde posible dentro de la ventana*: postergar la pausa maximiza el número de entregas que
se hacen antes y por lo tanto reduce la tardanza. La regla ingenua —comer en la primera
oportunidad— retrasa toda la ruta una hora sin ninguna contrapartida, y en pruebas volvía
infactibles rutas que sí caben. Dos pasadas de O(n) siguen siendo O(n).

Dos casos no consumen tiempo de ruta: si el recorrido termina antes de que abra la ventana, la
unidad come después; si empieza después de que cierra, comió mientras estaba ociosa.

**Sobre LE017.** El confinamiento de la ruta al turno admite dos lecturas: la estricta —la ruta
completa termina antes del cambio de turno— y la operativa —la asignación se hace dentro del
bloque, pero la unidad puede ser relevada y continuar—. Con la estricta, una bicicleta a 12 km/h
no cruza la ciudad dentro de una jornada de ocho horas menos la hora de alimentación, lo que deja
sin cobertura buena parte del mapa. El parámetro `limitarRutaAlTurno` selecciona una u otra sin
tocar el código; el valor por defecto es la lectura operativa.

---

## 4. Costo de inserción y verificación de factibilidad

Toda la verificación de restricciones vive en un único punto: el evaluador de inserciones. Es la
consecuencia práctica de la propiedad que motivó elegir ALNS en el ISA —las restricciones se
absorben en el operador de inserción sin reformular el modelo—. Añadir una restricción nueva
significa añadir una comprobación en `EvaluadorInsercion`, no rediseñar el algoritmo.

```
Δ(p, r, k) = contribución(r ⊕ (p, k)) − contribución(r)

contribución(r) = w_u + w_d · costoOperación(r) + w_p · tardíos(r) + w_m · tardanza(r)
                  [+ w_p · |r| si r es estructuralmente infactible]
```

```
FUNCIÓN mejorEnUnidad(solución, p, v, contexto, soloAdmisibles)
  SI q(p) > Q(τ(v))                          ENTONCES devolver ∅   // poda O(1)
  SI r(v) ≠ ∅ Y carga(r) + q(p) > Q(τ(v))    ENTONCES devolver ∅   // poda O(1)
  SI no hay stock en almacenOrigen(r)        ENTONCES devolver ∅   // poda O(1)

  SI r(v) = ∅ ENTONCES                        // abrir ruta nueva
      PARA cada almacén a con stock, ordenados por cercanía a pos(v)
          evaluar (a, posición 0) y quedarse con el mejor
  SINO
      PARA k = 0 .. |r|
          insertar p en k, recalcular, medir Δ, retirar p
      devolver el mejor k
```

La decisión de **qué almacén origina cada ruta** se toma aquí, al abrir una ruta nueva: es la
componente multi-almacén del problema. La rama de rutas no vacías cuesta O(n²) —n+1 posiciones por
una pasada O(n)—, acotada en la práctica porque las rutas rara vez superan diez paradas.

### 4.1 Caché incremental de inserciones

Un reparador goloso *global* —el que en cada paso elige el par (pedido, posición) más barato entre
todos los pendientes— es netamente mejor que insertar en orden fijo, pero recalcular todo en cada
paso cuesta O(q²·|V|·n²) por reparación y hace inviables las miles de iteraciones que el ALNS
necesita.

La observación que lo arregla: **el costo de insertar un pedido en una ruta solo depende de esa
ruta**. Al insertar en la unidad *U*, únicamente las entradas de *U* quedan obsoletas.

```
tabla : Map<Pedido, Map<codigoUnidad, Insercion>>

construcción inicial      O(q · |V| · n²)     una sola vez por reparación
tras cada inserción       O(q · n²)           refresco de una sola columna
```

Con eso el costo total de una reparación baja de O(q²·|V|·n²) a O(q·|V|·n² + q²·n²), que es lo que
permite ejecutar miles de iteraciones dentro del presupuesto de un ciclo.

---

## 5. Esquema del ALNS

```
ENTRADA : contexto del ciclo (pedidos, unidades, almacenes, mapa con bloqueos vigentes)
SALIDA  : asignación de rutas a unidades

 1  s   ← construcciónInicial(contexto)          // inserción golosa por criticidad
 2  s*  ← s ;  f* ← f(s)
 3  w_d ← 1 ;  w_r ← 1                           // pesos de los operadores
 4  T   ← calibrarTemperatura(parte mejorable de f(s))
 5  MIENTRAS iter < maxIter Y tiempo < presupuesto HACER
 6      d ← ruleta(w_d)                          // operador de destrucción
 7      r ← ruleta(w_r)                          // operador de reparación
 8      q ← gradoDestrucción(s)                  // adaptativo según ocupación de flota
 9      s′ ← copia(s)
10      d(s′, q)                                 // remover q pedidos
11      C ← removidos ∪ másCríticos(noAsignados(s′))
12      r(s′, C)                                 // reinsertar
13      colocarRezagados(s′, C)                  // despachar aunque llegue tarde
14      SI f(s′) < f*        ENTONCES s* ← s′ ; s ← s′ ; π ← σ₁
15      SINO SI f(s′) < f(s) ENTONCES s ← s′ ; π ← σ₂
16      SINO SI aceptar(f(s′), f(s), T) ENTONCES s ← s′ ; π ← σ₃
17      SINO π ← 0
18      w_d[d] += π ; w_r[r] += π
19      T ← T · c                                // enfriamiento geométrico
20      CADA L iteraciones: w ← λ·w + (1−λ)·π/θ  // actualización adaptativa de pesos
21      SI estancado: recalentar y reiniciar desde s*
22  DEVOLVER s*
```

### 5.1 Solución inicial

Inserción golosa secuencial en orden de **criticidad decreciente** —primero los pedidos con menos
minutos hasta su hora límite—. El orden no es estético: como un solo incumplimiento termina el
escenario, la solución de partida debe garantizar sitio a los urgentes antes de ocupar la flota con
los que aún tienen treinta horas de margen. Un pedido regular desplazado a un ciclo posterior no
cuesta nada; un priorizado desplazado puede costar el escenario.

Una primera pasada solo admite inserciones sin tardanza; una segunda coloca a los rezagados
aunque lleguen tarde, para que la función objetivo pueda verlos y la búsqueda tenga desde dónde
repararlos.

### 5.2 Grado de destrucción

```
ρ ← U(ρ_min, ρ_max)
SI destrucciónAdaptativaPorOcupación ENTONCES ρ ← ρ · (1 − 0,7 · ocupaciónFlota)
q ← recortar(round(ρ · |P|), q_min, q_max)
```

Con la flota casi vacía el algoritmo destruye mucho y explora agresivamente; con la flota saturada
—la antesala del colapso— destruye poco. Es la **mitigación concreta** de la limitación que el
ISA le reconoce al ALNS: cerca del punto de colapso el espacio factible es mínimo y remover un
porcentaje elevado de una solución apenas factible puede producir un estado que los operadores de
reparación no logren reconstruir dentro de plazo.

### 5.3 Criterio de aceptación

Recocido simulado: se acepta siempre si mejora, y con probabilidad `exp(−Δf / T)` si empeora, con
`T` decreciendo geométricamente hasta una fracción prefijada de `T₀` al agotar el presupuesto.

La temperatura inicial se calibra con la regla de Ropke y Pisinger, `T₀ = −(w · base) / ln 0,5`,
pero **la base no es f(s₀)**. En este problema f(s₀) suele estar dominada por un término
irreducible —los pedidos ya vencidos antes del ciclo, o los que ninguna unidad alcanza a tiempo—
que ninguna reordenación puede eliminar; calibrar sobre esa magnitud haría que el criterio
aceptara casi cualquier candidata y el ALNS degeneraría en una caminata aleatoria. Se calibra
entonces sobre la **parte mejorable**: costo de operación más costo fijo de las unidades en
servicio. El resultado es el deseado: la búsqueda explora libremente entre soluciones que difieren
en kilómetros, pero rechaza casi siempre las que añaden un incumplimiento o dejan un pedido sin
despachar. Es la estructura lexicográfica de la función objetivo trasladada al criterio de
aceptación.

### 5.4 Mecanismo adaptativo

Selección por ruleta con probabilidad proporcional al peso; al cerrar cada segmento de `L`
iteraciones los pesos se suavizan con `w ← λ·w + (1−λ)·(π/θ)` y los contadores se reinician. Los
pesos tienen cota inferior para que ningún operador quede permanentemente excluido, lo que preserva
la diversificación en fases tardías.

Es el rasgo que distingue al ALNS del LNS clásico y la razón por la que una misma implementación
sirve para los tres escenarios sin reprogramarse: los operadores útiles en la operación día a día
no son los mismos que dominan cerca del colapso, y el algoritmo lo descubre solo.

---

## 6. Cartera de operadores

### 6.1 Destrucción

| Operador | Criterio | Papel |
|---|---|---|
| `remocion-aleatoria` | q pedidos al azar | Diversificación pura; escapa de óptimos locales que los operadores dirigidos refuerzan |
| `remocion-peor` | mayor desvío + tardanza, con ranking sesgado `⌊y^D·\|L\|⌋` | Reubica lo que está mal colocado o genera incumplimiento |
| `remocion-shaw` | pedidos relacionados por distancia, hora límite y carga | Permite recombinaciones reales entre pedidos parecidos |
| `remocion-de-ruta` | vacía rutas completas | Único operador capaz de **reducir la flota en servicio** |
| `remocion-arco-bloqueado` | pedidos cuyos tramos cruzan una calle cerrada | **Aporte propio**: traduce el mecanismo de recuperación del enunciado |
| `remocion-averia` | pedidos de unidades no disponibles, luego los más críticos | **Aporte propio**: resuelve la avería en un solo ciclo destruir–reparar |

La medida de relación de Shaw combina tres términos normalizados:

```
R(i,j) = φ · d(i,j)/d_max + χ · |lim_i − lim_j|/T_max + ψ · |q_i − q_j|/q_max
```

Los dos últimos operadores son el aporte del equipo anunciado en el ISA. `remocion-averia` es lo
que materializa la ventaja atribuida al ALNS frente a la Búsqueda Tabú: la avería de una unidad
cargada se resuelve en **un solo ciclo**, mientras que un método de vecindario pequeño necesita
encadenar muchos movimientos individuales.

### 6.2 Reparación

| Operador | Criterio de orden | Criterio de ubicación |
|---|---|---|
| `insercion-golosa` | menor Δ global | menor Δ |
| `insercion-golosa-ruido` | menor Δ + ruido `η·d_max·U(−1,1)` | menor Δ |
| `insercion-arrepentimiento-2` | mayor `Σ_{i=2..k}(Δ_i − Δ_1)` | menor Δ |
| `insercion-arrepentimiento-3` | ídem con k = 3 | menor Δ |
| `insercion-compatibilidad-ventanas` | menor holgura | **mayor holgura mínima resultante** |

El goloso deja para el final los pedidos difíciles, que terminan sin sitio. No se corrige: es la
debilidad que compensan los operadores de arrepentimiento, y mantener ambos en la cartera es lo que
permite al mecanismo adaptativo elegir el conveniente en cada fase. Un pedido con menos de k
alternativas recibe arrepentimiento infinito y se coloca de inmediato —el caso de los priorizados
de 4 horas con la flota cargada—.

El reparador por compatibilidad de ventanas es el único que **no ordena por costo sino por riesgo
temporal**, y el único que prefiere la unidad que deja más margen en vez de la más barata. Aporta
robustez donde los otros aportan eficiencia: conservar holgura es lo que permite absorber los
bloqueos y averías de los ciclos siguientes.

---

## 7. Parámetros

| Parámetro | Por defecto | Papel |
|---|---|---|
| `maxIteraciones` | 3 000 | Presupuesto de iteraciones |
| `presupuestoMs` | 2 000 | Presupuesto de tiempo por ciclo |
| `gradoDestruccionMin/Max` | 0,10 / 0,35 | Fracción de pedidos removidos |
| `destruccionAdaptativaPorOcupacion` | sí | Contrae la destrucción con la flota saturada |
| `sesgoRemocionPeor` / `sesgoRemocionShaw` | 3,0 / 5,0 | Factor `D` del ranking sesgado |
| `shawPesoDistancia/Tiempo/Carga` | 0,6 / 0,3 / 0,1 | φ, χ, ψ de la relación de Shaw |
| `factorRuido` | 0,025 | Intensidad del ruido en los costos |
| `longitudSegmento` | 100 | Iteraciones entre actualizaciones de peso |
| `factorReaccion` | 0,80 | λ del suavizado exponencial |
| `σ₁ / σ₂ / σ₃` | 33 / 13 / 6 | Puntajes de nueva mejor, mejora y aceptación |
| `porcentajeAceptacionInicial` | 0,05 | w de la calibración de T₀ |
| `maxPedidosPorReparacion` | 120 | Cota del tamaño de una reparación |
| `maxPedidosPorCiclo` | 400 | Cota de pedidos entregados al algoritmo |
| `ventanaDisponibilidadUnidadMinutos` | 240 | Anticipación con que se asigna a una unidad ocupada |

Los dos últimos son salvaguardas de escalabilidad, no recortes de alcance. La capacidad instalada
por ciclo está acotada por la suma de capacidades de la flota (408 paquetes con la configuración
por defecto); considerar miles de pedidos que ninguna unidad podrá tomar multiplica el costo de
cada evaluación sin mejorar el plan, y reduce las iteraciones a unas pocas. Los pedidos excluidos
no se pierden: vuelven a competir en el ciclo siguiente con mayor criticidad. Análogamente, una
unidad que regresa dentro de nueve horas no es capacidad real del ciclo actual, y asignarle
pedidos solo produce rutas que empiezan tardísimo.

Perfiles preconfigurados en `PlanificadorALNS`: `paraOperacionDiaria()` (2 000 iter / 1,5 s),
`paraSimulacion5D()` (1 200 iter / 0,4 s, para que cientos de ciclos quepan en la ventana de 30 a
60 minutos reales de LE058) y `paraColapso()` (4 000 iter / 3 s, con destrucción más conservadora).

---

## 8. Trazabilidad con la Lista de Exigencias

| Exigencia | Dónde se satisface |
|---|---|
| LE003, LE009 — carga determinista del archivo de ventas | `CargadorVentas`, numeración correlativa por orden de archivo |
| LE006 — todos los pedidos registrados entran al ciclo | `ContextoPlanificacion.construir` |
| LE008 — reproducibilidad | Semilla única + colecciones con orden estable |
| LE014, LE027 — capacidad por unidad | Poda O(1) en `EvaluadorInsercion`; verificación en `Ruta.recalcular` |
| LE015, LE016 — hora estimada y hora límite | Pasada hacia adelante de `Ruta.simular` |
| LE017 — asignación dentro del turno | `ParametrosPlanificador.limitarRutaAlTurno` |
| LE018 — hora de alimentación | `Ruta.ubicarAlimentacion` |
| LE019, LE032 — inventario no negativo | `Solucion.consumoAlmacen` y `Solucion.hayStock` |
| LE020 — retorno a un almacén | `ContextoPlanificacion.almacenMasCercano` |
| LE025 — calles de doble sentido | `MapaUrbano`: los arcos se cierran en ambos sentidos |
| LE026 — ciclo de 15 minutos | `ParametrosPlanificador.minutosPorCicloPlanificacion` |
| LE075, LE084 — bloqueos por intervalo | `Bloqueo.arcosUnitarios`, `MapaUrbano.fijarInstante` |
| LE087, LE089, LE098 — unidades averiadas | Filtro de asignables y `RemocionPorAveria` |
| LE096, LE097 — prioridad por tiempo restante | `Pedido.criticidad`, orden del constructor inicial y del reparador por ventanas |
| LE088, LE094 — replanificación de afectados | `ALNS.resolver(ctx, planPrevio)` con herencia del plan vigente |

---

## 9. Complejidad

| Operación | Costo |
|---|---|
| Distancia entre dos nodos, sin bloqueos | O(1) |
| Distancia entre dos nodos, con bloqueos | O(V + E) la primera vez por origen, O(1) memorizada |
| Recálculo de una ruta | O(n) |
| Mejor inserción en una ruta | O(n²) |
| Mejor inserción en la solución | O(\|V\| · n²) |
| Reparación completa con caché | O(q · \|V\| · n² + q² · n²) |
| Iteración del ALNS | destrucción O(\|P\|·log\|P\|) + reparación |

---

## 10. Resultados de la verificación

Sobre `ventas.202811` con `bloqueo.2811`, ciclo del día 15 a las 12:00, 254 pedidos y 37 unidades:

```
[1] Heurística constructiva : f = 310 243 240,74  (3 188 km · S/ 19 420 · 80 tardíos · 162 diferidos)
    ALNS                    : f = 296 441 807,96  (3 250 km · S/ 20 290 · 100 tardíos · 154 diferidos)
    6 000 iteraciones en 2 461 ms
[2] Repetición con la misma semilla: f idéntica          → reproducibilidad (LE008)
[3] 254 pedidos del ciclo, 254 referenciados             → sin duplicados ni pérdidas (LE006)
[4] Ninguna ruta excede la capacidad de su unidad        → LE014, LE027
[5] Ningún almacén intermedio con stock negativo         → LE019
```

La mejora es instructiva: el ALNS **gasta más kilómetros y acepta más incumplimientos** para
despachar ocho pedidos más. Es exactamente el intercambio que la función objetivo prescribe —cada
pedido colocado ahorra hasta 1 750 000 puntos, cada incumplimiento cuesta 200 000— y confirma que
la estructura lexicográfica está operando como se diseñó.

En el escenario de operación día a día sobre `ventas.202601`, 96 ciclos consecutivos entregan 114
pedidos con 13 fuera de plazo y usan efectivamente los dos almacenes intermedios como origen de
ruta, lo que verifica la componente multi-almacén.

---

## 11. Qué falta

1. **Búsqueda Tabú.** Implementar `PlanificadorTS` contra la interfaz `Planificador`, reutilizando
   `Ruta`, `Solucion` y `EvaluadorInsercion`. Movimientos `relocate`, `exchange` y `2-opt*`, lista
   tabú sobre pares (pedido, unidad) y criterio de aspiración por mejor solución conocida. Usar la
   misma función objetivo es condición para que la comparación sea válida.
2. **Simulador.** `DemoPlanificador` es un arnés que despacha las rutas completas; el simulador
   definitivo necesita reloj continuo, generación de averías según la tasa configurable (LE078),
   detección de colapso (LE021) y reasignación de pedidos en camino (LE091).
3. **Experimentación numérica.** Diseño de experimento con réplicas por semilla, las tres
   instancias de escenario y los indicadores de LE062 a LE064.
4. **Calibración.** Los parámetros están en los rangos de la literatura, no calibrados para estas
   instancias. Los candidatos con más impacto esperado son `gradoDestruccionMax`,
   `factorReaccion` y `horizonteAtencionMinutos`.
