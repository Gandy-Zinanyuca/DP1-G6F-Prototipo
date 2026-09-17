# PaqRap — Componente planificador (Tabu Search)

Implementación de Tabu Search sobre el contrato estricto compartido con ALNS.
JDK 17+, sin Maven ni librerías externas.

El [diseño del algoritmo](DISENO-ALGORITMOS.md) contiene formulación, pseudocódigo,
estructuras, parámetros, restricciones y trazabilidad. La [verificación](../VERIFICACION-TABU.md)
contrasta esta versión con el cuadro inicial.

## Contenido

| Clase | Responsabilidad |
|---|---|
| TabuSearchPlanner | Bucle TS, mejor solución, alternancia de vecindarios y parada |
| ConfiguracionTabu | Iteraciones, tenencia, estancamiento, candidatos, tiempo y semilla |
| AssignmentNeighborhood | Inserción de pendientes, transferencia y retiro de partes |
| RoutingNeighborhood | Swap y relocate intra-ruta |
| TabuMove | Atributos consultados y movimiento inverso |
| TabuList | Expiración por iteración |
| CandidateSelector | Factibilidad dura, aspiración y selección por objetivo |

Las clases operativas, caminos, constructor, evaluación y métricas están en
`comun/src/main/java/pe/pucp/paqrap/estricto/`.
No se importa ALNS desde Tabu ni desde el núcleo común.

## Compilar y verificar

Desde la raíz del repositorio:

```bat
algoritmos\compilar.bat
```

En Linux/macOS:

```sh
bash algoritmos/compilar.sh
```

Se ejecutan las tres pruebas históricas y los 15 grupos de regresiones estrictas.
Para repetir únicamente las nuevas pruebas:

```sh
java -cp algoritmos/out pe.pucp.paqrap.RestriccionesEstrictasTest
```

## Ejecutar experimentación numérica

Para ejecutar únicamente Tabu Search y obtener un resumen por consola:

```bat
algoritmos\ejecutar-tabu.bat algoritmos\datos-locales\ventas.202609.txt algoritmos\datos-locales\bloqueo.2609.txt algoritmos\datos-locales\mant.preventivo.09.10.txt 2026-09-01T08:00 100 20262
```

Para ejecutar únicamente ALNS estricto:

```bat
algoritmos\ejecutar-alns.bat algoritmos\datos-locales\ventas.202609.txt algoritmos\datos-locales\bloqueo.2609.txt algoritmos\datos-locales\mant.preventivo.09.10.txt 2026-09-01T08:00 100 20262
```

La [guía de arquitectura y ejecución](../ARQUITECTURA-EJECUCION.md) documenta la salida,
las clases, los métodos principales y los diagramas de secuencia.

Para ejecutar ambos motores y guardar resultados comparables:

```sh
java -Xmx768m -cp algoritmos/out pe.pucp.paqrap.CompararAlgoritmos algoritmos/datos-locales/ventas.202609.txt algoritmos/datos-locales/bloqueo.2609.txt algoritmos/datos-locales/mant.preventivo.09.10.txt 2026-09-01T08:00 algoritmos/experimentacion/resultados/estricto-202609 20 20262,20263,20264
```

El comando ejecuta TS y ALNS estrictos, con 20 iteraciones máximas y las tres semillas
indicadas. Para octubre, cambiar ventas a `ventas.202610.txt`, bloqueos a
`bloqueo.2610.txt`, instante a `2026-10-01T08:00` y carpeta final a `estricto-202610`.
En PowerShell también puede citarse la lista de semillas: `"20262,20263,20264"`.

Las copias de datos reales se encuentran en `algoritmos/datos-locales/`, ignorada por Git.
El ejecutable exige TXT extraídos: no abre ZIP ni depende de rutas personales.
Los últimos dos argumentos son opcionales: 20 iteraciones y las semillas anteriores.
El directorio de salida contendrá:

- `metricas.csv`: métricas homogéneas por algoritmo/semilla.
- `README.md`: parámetros, selección del snapshot y SHA-256 de entradas.
- `TS-estricto-SEMILLA.md` y `ALNS-estricto-SEMILLA.md`: partes, horarios,
  descanso, retorno y caminos con horas por arco.

Los archivos del mismo experimento se sobrescriben al repetir el comando.
El [resumen de resultados](../experimentacion/COMPARACION-ESTRICTA.md) explica el alcance.

## Uso desde backend o pruebas Java

```java
ParametrosOperacion parametros = ParametrosOperacion.porDefecto();
ConfiguracionTabu config = new ConfiguracionTabu(100, 7, 40, 1000, 0, 20262L);
ResultadoPlanificacion resultado =
    new TabuSearchPlanner(config).planificar(estadoOperacion, parametros);
```

Importar `pe.pucp.paqrap.estricto.modelo.*` y `pe.pucp.paqrap.tabu.*`.
El backend construye el snapshot inmutable; TS no lee archivos ni avanza el reloj.
Para cargar TXT usar `pe.pucp.paqrap.estricto.datos.DatasetLoader`.
Con el mismo snapshot, `ALNSPlanner` implementa `PlanificadorEstricto`.

## Interpretación de la salida

`evaluacion.factible()` garantiza las restricciones de las rutas que se asignaron.
`evaluacion.completa()` exige además que no queden paquetes pendientes.
**Un resultado incompleto no resuelve toda la demanda**, aunque ninguna ruta viole restricciones.
Los pedidos imposibles o vencidos permanecen pendientes; no se cancelan ni se entregan tarde.

La replanificación conserva rutas en curso que siguen siendo factibles. Si una queda
invalidada, devuelve sus partes pendientes a la construcción común para reasignarlas.
El auxilio es reasignación de entregas; el encuentro físico entre vehículos y la ejecución
de eventos siguen siendo responsabilidad externa.
