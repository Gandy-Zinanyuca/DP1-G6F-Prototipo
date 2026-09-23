# TS y ALNS: experimentacion pareada

JDK 17, sin Maven. Ejecutar desde la raiz del repositorio. Los dos motores usan el mismo EstadoOperacion inmutable, constructor inicial determinista, evaluador y caminos.

## Compilar y verificar

~~~bat
algoritmos\compilar.bat -Pruebas
~~~

Incluye las 15 regresiones compartidas y las pruebas de holgura, completitud, colapso, reproducibilidad y exportacion. TS tambien dispone de:
~~~bat
java -cp algoritmos/out pe.pucp.paqrap.RestriccionesTabuTest
~~~

## Una ejecucion por algoritmo

~~~bat
algoritmos\ejecutar-tabu.bat algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 20 20262 0 --salida algoritmos/experimentacion/resultados/ts-01
algoritmos\ejecutar-alns.bat algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 20 20262 0 --salida algoritmos/experimentacion/resultados/alns-01
~~~

Argumentos: ventas, bloqueos, mantenimiento, instante, [iteraciones=100], [semilla=20262], [presupuesto-ms=0]. Las opciones con -- se colocan despues de los argumentos posicionales. La ejecucion individual siempre exporta un CSV propio; sin --salida crea una carpeta unica en algoritmos/experimentacion/resultados.

## Comparacion pareada

~~~bat
java -cp algoritmos/out pe.pucp.paqrap.CompararAlgoritmos algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 algoritmos/experimentacion/resultados/pares-01 20 20262,20263,20264 0 --escenario E1 --carga base --instancia septiembre-01
~~~

Para cada semilla ejecuta TS y luego ALNS sobre la misma entrada, con caches y generadores aleatorios nuevos. Cada motor reconstruye la misma solucion inicial determinista dentro de Ta. La inmutabilidad conserva las condiciones iniciales, y se verifica que el estado no cambie. Hay dos iteraciones de calentamiento por motor excluidas de los resultados.

Genera:

- metricas.csv: todas las ejecuciones.
- TS-estricto.csv y ALNS-estricto.csv: resultados separados.
- README.md: parametros, hashes de entrada, configuracion y entorno.
- estado.txt: contenido exacto de la entrada preparada.
- Un informe por algoritmo/semilla/repeticion con rutas, horarios y pendientes.

La carpeta debe ser nueva o vacia. Los resultados se escriben por corrida; un colapso no interrumpe las demas repeticiones. Un error de entrada o una solucion invalida si interrumpe la ejecucion, para no confundir defectos con colapso.

## Escenarios y niveles

| Opcion | Significado |
| --- | --- |
| --escenario E1/E2/E3 | Identificador experimental; no altera automaticamente las incidencias |
| --carga etiqueta | Nombre del nivel de carga |
| --instancia etiqueta | Identificador de la instancia |
| --factor-carga 1.5 | Multiplica cantidades y redondea hacia arriba; conserva pedidos, destinos y deadlines |
| --max-pedidos 400 | Limite externo de pedidos considerados |
| --horizonte-horas 24 | Incluye deadlines hasta T + horizonte |
| --averias archivo.txt | Intervalos: vehiculo;inicio-ISO;fin-ISO |
| --salida carpeta | Solo ejecucion individual; el comparador tiene salida posicional |

Para E2 repetir el comparador con factores crecientes (por ejemplo 1, 1.5, 2), la misma lista de semillas y una carpeta diferente por nivel. Para E3 proporcionar los archivos de bloqueos/mantenimiento correspondientes y, cuando aplique, --averias. E1 usa las condiciones base de los archivos: la etiqueta no elimina bloqueos ni mantenimientos.

Las mismas etiquetas, archivos, instante y factor deben usarse para los dos algoritmos; el comparador lo hace automaticamente. El estado_sha256 permite comprobar identidad del estado en los CSV.

## Interpretacion

- COMPLETA: todos los paquetes tienen una entrega planificada factible.
- COLAPSO_PLANIFICACION: al terminar la busqueda quedan paquetes pendientes.
- SIN_DEMANDA: no hay pedidos considerados; no cuenta como colapso ni como exito con entregas.

La consola muestra Ta (ms), holgura promedio y minima (min), distancia (km), suma de tiempos de rutas (min), vehiculos utilizados, utilizacion de capacidad, costo, cobertura y pendientes. Tambien muestra iteraciones, primera solucion completa, mejor iteracion y motivo de parada.

La holgura es deadline menos fin de servicio de la ultima parte del pedido, con una observacion por pedido. Se exporta solo para soluciones completas no vacias. En colapso y sin demanda queda vacia en CSV y se muestra N/A. Nunca sustituirla por cero para el analisis.

Los CSV incluyen las corridas fallidas. Para comparar holgura de forma pareada, emparejar por escenario, carga, instancia, estado_sha256 y semilla y usar pares completos; reportar tambien colapsos y cantidad de pares excluidos. Ta se registra incluso en colapso.

## Alcance y reproducibilidad

El objetivo prioriza menos paquetes pendientes y despues mayor holgura promedio; costo y distancia son complementarios. Consultar [definiciones y metadatos](METADATOS-PRUEBAS.md).

Estas ejecuciones son instantaneas de planificacion, con una salida por vehiculo. No reconstruyen entregas anteriores ni simulan todo el mes: pedidos antiguos vencidos pueden provocar colapso inmediato. Preparar un lote representativo antes del estudio. La carga por defecto limita a 400 pedidos con registro <= T y deadline <= T+24h; las exclusiones quedan registradas.

Con presupuesto 0 se reproduce el recorrido con la misma semilla, configuracion y version de codigo. Ta siempre puede variar. Un limite temporal positivo es cooperativo y puede excederse; igual numero de iteraciones no representa igual trabajo para TS y ALNS.

Las carpetas de resultados estan ignoradas por Git; los antiguos archivos quedan locales y en el historial. Registrar el commit y si hay cambios locales junto con los metadatos para identificar la version ejecutada.

El simulador mensual historico de algoritmos/alns/src/pe conserva su contrato propio. Sus CSV y analisis de dias hasta colapso no se mezclan con esta comparacion por EstadoOperacion. Ver [ALNS](alns/README.md) y [TS](tabu/README.md).
