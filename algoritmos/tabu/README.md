# Planificador logístico con Tabu Search

Java 17+, Maven y JUnit 5. Dos módulos: asignación entre vehículos y ordenamiento dentro de cada ruta. Incluye los datos publicados del 09/09/2026, configuración portable y una demo sintética. No incorpora GUI, API ni base de datos.

## Ejecutar los datos publicados

Desde esta carpeta:

```shell
java -jar planificador-tabu.jar --config config/referencia-20260909.properties
```

El escenario planifica el **09/09/2026 a las 00:00**, con **Sa=30 min, K=4, Sc=120 min, 100 iteraciones y tenencia 7**. Estos últimos números son parámetros de experimentación del MVP, no valores prescritos por el Excel.

La CLI sobrescribe los valores del archivo:

```shell
java -jar planificador-tabu.jar --config config/referencia-20260909.properties --tiempo 2026-10-01T08:00 --iteraciones 50
java -jar planificador-tabu.jar --config config/referencia-20260909.properties --velocidad-ta 25 --detalle-caminos
```

Las rutas de datos del `.properties` se resuelven respecto a su propia carpeta. Para mover el proyecto, conserva `config`, `datos`, `src` y `pom.xml`. Los datos viajan en `datos`, fuera del JAR; no se requiere la carpeta Downloads.

Sin argumentos se ejecuta la **demo sintética original**, identificada como tal en el reporte:

```shell
java -jar planificador-tabu.jar
java -jar planificador-tabu.jar --ayuda
```

## Compilar y probar

Con JDK 17+ y Maven instalado:

```shell
mvn clean verify
java -jar target/planificador-tabu-1.0.0.jar --config config/referencia-20260909.properties
```

En el entorno Windows de esta entrega, javac falla al resolver rutas reales de algunos JAR de prueba. Se incluye un perfil opcional con Eclipse ECJ, nivel de lenguaje y bytecode 17, que no añade dependencias al ejecutable:

```shell
mvn -Pcompilador-eclipse clean verify
```

La verificación terminó con **66 pruebas aprobadas, sin errores ni omisiones**. La suite valida todos los parsers sobre los 160,010 pedidos y 21,725 bloqueos, restricciones, vecindarios, memoria tabú, aspiración, configuración relativa, clientes repetidos, ventanas entre meses y mantenimiento bimensual. `verificacion-maven.txt` contiene el resultado final.

La ejecución publicada de 100 iteraciones obtuvo **19/19 pedidos dentro del plazo**, 7 vehículos utilizados, 618 km y **S/ 4,664.00**, frente a S/ 5,962.00 de la solución inicial. La suma de duraciones de rutas fue 53.2929 h, con 81.55% de utilización promedio. Se evaluaron 37,170 candidatos (los vecindarios ya no generan los que exceden la capacidad del vehículo, conforme al pseudocódigo 5.1). Los reportes `resultado-publicado.txt` y `resultado-publicado-caminos.txt` contienen todas las métricas y las trazas de la misma ejecución; Ta varía según el equipo.

## Referencias actualizadas

| Tipo | Unidades | Capacidad por ruta | Velocidad | Costo/km |
|---|---:|---:|---:|---:|
| TA / Auto | 10 | 24 | 20 km/h | S/ 8 |
| TM / Moto | 15 | 8 | 40 km/h | S/ 6 |
| TB / Bicicleta | 12 | 4 | 14 km/h | S/ 3 |

Central: **(27,14)**. Intermedios de referencia: **NO (12,38)** y **Este (57,27)**. El MVP sale y regresa al central, sin recargas intermedias. La retícula tiene 71×51 nodos inclusivos [0,70]×[0,50], separados 1 km, sin diagonales.

El deadline publicado se aplica a la **llegada**, permitiendo igualdad. La hora de acondicionamiento queda fuera del plazo, pero ocupa al vehículo y retrasa las siguientes entregas. El regreso cuenta en distancia, tiempo, costo y mantenimiento.

**REFERENCIAS.md** identifica las celdas fuente y las discrepancias resueltas. Las velocidades 40/25/12, origen (0,0), cierre solo de calles y deadline al finalizar el servicio permanecen en la demo legada y en el constructor de EstadoOperacion de cinco argumentos. Para las reglas publicadas se pasa `ParametrosOperacion.publicados()` como sexto argumento.

## Datos y formatos

- `datos/ventas`: 36 TXT, enero 2026–diciembre 2028.
- `datos/bloqueos`: 36 TXT del mismo periodo.
- `datos/mant.preventivo.09.10.txt`: 37 registros originales, repetidos en memoria cada dos meses para generar 888 fechas entre 2026 y 2029.
- `datos/referencias`: Excel original sin modificaciones.
- `datos/inventario.json`: cantidad de registros y SHA-256 de cada TXT mensual.

`--datos datos` carga el conjunto publicado. No se combina con `--pedidos`, `--bloqueos` o `--mantenimientos`. También se pueden usar archivos individuales:

```shell
java -jar planificador-tabu.jar --pedidos datos/ventas/ventas.202609.txt --bloqueos datos/bloqueos/bloqueo.2609.txt --mantenimientos datos/mant.preventivo.09.10.txt --anio 2026 --mes 9 --tiempo 2026-09-09T00:00
```

Con archivos individuales el mantenimiento se usa tal cual; la expansión bimensual corresponde a `--datos`. La flota real por defecto tiene 37 vehículos. `--vehiculos TA01,TM01,TB01` permite indicar una flota explícita; todos los códigos del mantenimiento suministrado deben pertenecer a ella.

En ventas, `cIdCliente` identifica al cliente y puede repetirse. Se conserva como `Pedido.clienteId`, generando un `Pedido.id` por mes y línea, por ejemplo `V202609-L01454`. Dos ventas del mismo cliente no se descartan ni fusionan. El método `parsear` de una línea aislada conserva el identificador recibido para compatibilidad; al cargar archivos se generan los IDs únicos.

Se usan `java.time`, UTF-8, BOM opcional, comentarios completos con # y líneas vacías. Se validan fechas, cantidades y plazos positivos, coordenadas y segmentos horizontales/verticales no vacíos. Los TXT publicados cumplen el formato. Cada archivo de bloqueos asigna ambos extremos al año/mes indicado; para otros proveedores con intervalos que crucen de mes se deben dividir o crear objetos con fechas completas desde Java.

## Configuración

`--config ARCHIVO` lee propiedades UTF-8 y rechaza claves desconocidas. Cada llamada usa una instantánea inmutable; los cambios se aplican en la próxima ejecución del planificador.

| Opción | Significado |
|---|---|
| `--tiempo`, `--sa`, `--k` | T y ventana inclusiva [T,T+Sa×K]; minutos y factor entero positivo |
| `--iteraciones`, `--tenencia` | Parada y memoria tabú; 0 iteraciones evalúa solo la inicial |
| `--central`, `--noroeste`, `--este` | Coordenadas x,y; intermedios como referencia |
| `--velocidad-ta`, `--velocidad-tm`, `--velocidad-tb` | Velocidad por tipo, decimal, entre 1 y 300 km/h |
| `--servicio` | Minutos positivos por atención; publicado 60 |
| `--plazo` | LLEGADA o FIN_SERVICIO |
| `--bloqueo-nodos` | true: calles incidentes a los nodos; false: solo los tramos |
| `--detalle-caminos` | Traza de cada calle unitaria y tiempos de cruce |

Los intervalos de bloqueo son [inicio,fin). Se comprueban durante toda la ruta, también después de Sc. El modo de nodos es conservador: cierra todas las calles incidentes durante el bloqueo, impidiendo atravesar o girar por esas intersecciones. Se permite esperar a la reapertura. No se simulan maniobras reactivas ni averías.

Los pedidos se cargan antes de salir. Salida = máximo de T, disponibleDesde y registros de todos los pedidos de la ruta. La capacidad es su cantidad acumulada, sin recargas ni fraccionamiento. El mantenimiento impide operar durante el día completo y se comprueba hasta el regreso. No se aceptan tardanzas mediante penalizaciones.

## Módulos y algoritmo

Paquete base `pe.logistica`:

| Componente | Responsabilidad |
|---|---|
| model | Valores inmutables, parámetros y referencia de flota |
| data | Parsers, carpetas mensuales y expansión de mantenimiento |
| routing | Retícula y Dijkstra temporal |
| tabu.AssignmentNeighborhood | Relocate inter-ruta en todas las posiciones de inserción |
| tabu.RoutingNeighborhood | Swaps dentro de una ruta |
| tabu.SolutionEvaluator | Validación común de restricciones duras |
| tabu.InitialSolutionGenerator | Inserción constructiva por deadline |
| tabu.TabuSearchPlanner | Actual/mejor solución, vecindarios, tabú, aspiración y parada |
| metrics, ConsoleReport | Indicadores y trazas auditables |

La inicial inserta por deadline y menor costo factible. Si no encuentra inserción, devuelve un diagnóstico parcial: esto no demuestra que el problema carezca de solución. Cada iteración examina ambos vecindarios sobre la misma solución y elige el menor costo admisible. Permite empeorar el costo para explorar, nunca la factibilidad.

Mover un pedido A→B prohíbe devolverlo a A durante las siguientes t iteraciones. Swap registra el par no ordenado de pedidos y el vehículo. Aspiración admite un candidato tabú solo si mejora estrictamente el mejor costo global. Se detiene por máximo de iteraciones o falta de candidatos admisibles. No hay aleatoriedad ni otra metaheurística.

Dijkstra temporal encuentra una llegada temprana, mediante rodeos o espera; no es una metaheurística. Tabu Search minimiza los costos del evaluador, sin garantía de óptimo global conjunto de asignación, orden, caminos y espera. El tiempo de cada kilómetro se redondea hacia arriba a nanosegundos para evitar llegadas físicamente anticipadas.

## Uso desde Java

```java
var config = new ConfiguracionTabu(30, 4, 100, 7);
var t = LocalDateTime.of(2026, 9, 9, 0, 0);
var datos = new DatasetLoader().cargar(Path.of("datos"), t, config.scMinutos());
var estado = new EstadoOperacion(t, datos.pedidos(),
    ReferenciaProyecto.flota(ReferenciaProyecto.CENTRAL),
    datos.bloqueos(), datos.mantenimientos(), ParametrosOperacion.publicados());
var resultado = new TabuSearchPlanner().ejecutar(estado, config);
ConsoleReport.imprimir(resultado, System.out, false);
```

ResultadoPlanificacion conserva solución, evaluación, métricas y parámetros utilizados. Cada ResultadoRuta conserva visitas y caminos unitarios con horarios. Para cambiar velocidades se construye otro ParametrosOperacion en la siguiente llamada, sin mutar globalmente TipoVehiculo.

## Métricas y límites

El reporte muestra Ta, iteraciones, candidatos, factibles, tabú rechazados, aspiraciones, iteración del mejor resultado, costo inicial y motivo de parada. También factibilidad, costo, km, horas, pedidos considerados/asignados/faltantes/a tiempo, cumplimiento, vehículos usados, capacidad por vehículo, promedios y distribución por tipo, T, Sa, K, Sc, máximo de iteraciones y tenencia.

Ta usa System.nanoTime: incluye preparación, inicial y búsqueda; excluye lectura de archivos, agregación final de indicadores e impresión. El tiempo total suma duraciones de ruta desde salida a regreso, incluido servicio y espera en ruta. Excluye espera previa en el depósito y no representa duración de operaciones en paralelo.

Candidatos incluye el resultado inicial y cada propuesta de los vecindarios que respeta la capacidad del vehículo —el pseudocódigo 5.1 no genera las que la exceden—, incluidas las inviables por plazo, mantenimiento o bloqueo y las evaluaciones en caché. Inserciones constructivas se cuentan aparte y también omiten los destinos sin capacidad o no disponibles. Aspiración cuenta candidatos admitidos por esa regla, aunque se elija otro. La iteración inicial es 0 y una iteración examinada sin candidato admisible también cuenta.

Cumplimiento = entregables/considerados; es 100% sin pedidos. Las medias usan vehículos utilizados y valen 0 cuando no hay ninguno. Una ruta con incumplimiento duro no cuenta como entregable.

El MVP conserva una ruta por vehículo, sin stock, recargas, entregas parciales, múltiples viajes, averías ni simulación continua. Sa registra el salto previsto, pero no crea un ejecutor periódico ni arrastra pedidos históricos entre llamadas. Cada ejecución considera exclusivamente [T,T+Sc].

Códigos de salida: 0 factible/ayuda, 1 inicial no factible, 2 error de entrada. `resultado-publicado.txt` presenta el escenario real actualizado; `resultado-demo.txt` presenta la demo legada. Los detalles de procedencia y las diferencias con el proyecto completo del curso están en REFERENCIAS.md.
