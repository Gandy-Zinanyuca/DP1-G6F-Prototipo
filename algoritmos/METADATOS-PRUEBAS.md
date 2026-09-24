# Metadatos y reglas experimentales

## Protocolo

Cada combinacion escenario/nivel/instancia se prepara fuera de los motores. CompararAlgoritmos carga una sola entrada inmutable, ejecuta TS y ALNS con la misma semilla y repite con cada semilla especificada. Ambos reconstruyen la misma inicial determinista; no reutilizan caches ni estado aleatorio de otra corrida. El calentamiento tiene dos iteraciones por motor y se excluye de las filas.

## Objetivo y completitud

EvaluadorFactibilidad minimiza:
~~~
paquetesPendientes + 0.5 - atan(holguraMediaDePedidosCompletos / 60) / pi
~~~

Si no hay pedidos completos, el termino de calidad vale 0.5. Este termino esta estrictamente entre 0 y 1, de modo que un paquete pendiente menos siempre tiene prioridad. Entre soluciones completas el objetivo es estrictamente decreciente respecto a la holgura media. El costo monetario no interviene. El parametro historico penalizacionPaquetePendiente se conserva por compatibilidad, pero ya no determina este objetivo.

La busqueda puede explorar soluciones incompletas. Ambos motores conservan la mejor solucion, por lo que una completa encontrada no puede ser desplazada por una incompleta. La ausencia de completitud al final se registra como COLAPSO_PLANIFICACION; no demuestra imposibilidad matematica.

## Metricas y valores ausentes

| Campo | Definicion |
| --- | --- |
| Ta_ms | Tiempo de inicializacion, construccion, busqueda y calculo del resultado; excluye carga, auditoria y exportacion |
| holgura_promedio_min | Promedio por pedido de deadline menos fin de servicio de su ultima parte |
| holgura_minima_min | Minimo de esos margenes |
| Ta_primera_completa_ms | Tiempo hasta primera solucion completa encontrada; incluye inicial |
| iteracion_primera_completa | 0 si la inicial es completa; vacio si no se encuentra |
| iteracion_mejor | Iteracion de la mejor solucion devuelta; 0 para inicial |
| distancia_km | Suma de desplazamientos, incluyendo acceso a almacen y retorno |
| tiempo_rutas_min | Suma de salida a retorno, incluyendo servicio, espera y descanso |
| vehiculos | Vehiculos con ruta no vacia |
| utilizacion_capacidad | Carga asignada / suma de capacidades de vehiculos utilizados; fraccion 0..1 |
| cobertura_pedidos / cobertura_paquetes | Fracciones 0..1 sobre demanda de la entrada |
| costo_soles | Costo fijo por vehiculo y distancia, solo complementario |
| colapso | Si quedan paquetes pendientes en una entrada no vacia |
| instante_colapso | Instante de la entrada evaluada; no tiempo simulado transcurrido |
| causa_colapso / parada | SIN_SOLUCION_COMPLETA y razon de fin de busqueda; no diagnostico de imposibilidad |

Holguras vacias cuando hay colapso o no hay demanda. No promediar solo pedidos faciles de una corrida incompleta. SIN_DEMANDA debe separarse del denominador al reportar tasa de exito/colapso. Los pedidos completos son entregas planificadas, no entregas ya ejecutadas por un simulador.

## Restricciones comunes

Servicio 60 min, plazo incluye fin de servicio, turno 480 min con origen 07:00, alimentacion 60 min con INICIO en banda relativa [60,420], retorno sin restriccion de turno (el relevo se hace donde este la unidad). Para 07:00-15:00 puede comenzar entre 08:00 y 14:00 y terminar a las 15:00. Si las entregas no caben en el turno vigente, la ruta puede esperar al inicio de un turno posterior siempre que servicio, descanso y deadline sigan siendo factibles. Se aplican capacidad, stock, disponibilidad, bloqueos temporales. Averias y mantenimiento se conservan en el modelo operativo, pero se excluyen de la experimentacion. Partes de hasta 4 unidades. La flota y almacenes son los del cargador comun.

En simulacion continua, una hora ininterrumpida de inactividad dentro de la banda acredita la alimentacion del turno. Solo se considera tiempo posterior al inicio de simulacion y al ultimo retorno; una pausa parcial interrumpida por ruta no se acumula. El estado instantaneo que se entregue desde otro backend debe informar los descansos reales. No mezclar campañas anteriores a esta regla con las nuevas para comparar calidad.

La ruta prueba horarios de descanso y prefiere menores tiempos finales por pedido; la evaluacion global utiliza la ultima parte entre todos los vehiculos. Es una heuristica de horarios, no una optimizacion exacta.

## Configuracion y trazabilidad

TS: tenencia 7, hasta 400 candidatos por iteracion. ALNS: destruccion maxima 4 partes, segmento 5, reaccion 0.7, temperatura inicial 0.05 en unidades del nuevo objetivo, enfriamiento hasta el 1% al final. ALNS conserva dos destrucciones (aleatoria/cercania) y dos ordenes de reparacion (plazo/aleatorio). No incorpora aun los operadores especializados de incidencia del ALNS historico.

Las columnas identifican escenario, nivel, factor, instancia, hash del estado, semilla, repeticion, algoritmo, instante, limites y resultados. README generado incluye hashes de archivos, parametros operativos, version Java y entorno; estado.txt conserva la entrada. Registrar tambien git rev-parse HEAD y git status --short. CSV UTF-8, separador coma, punto decimal, comillas escapadas y ausentes como celdas vacias.

Las filas TS y ALNS se emparejan por combinacion y semilla. Conservar todos los colapsos y reportar tasa de colapso por motor; holgura pareada solo donde ambos completaron. Esta seleccion debe declararse en el analisis. No equiparar candidatos o iteraciones como esfuerzo identico.

## Limites

El cargador lee un mes y selecciona por instante y horizonte; no reconstruye operaciones previas. Factor de carga escala cantidades con techo. Las etiquetas E1/E2/E3 no generan incidencias automaticamente. Los archivos determinan los bloqueos; no se admite --averias y el mantenimiento se excluye. Rutas en curso pueden suministrarse por la API Java, pero no desde el CLI actual.

El comparador evalua una combinacion por invocacion. Para estimar ultimo nivel soportado se debe ejecutar la secuencia de niveles con la misma instancia y semillas y analizar sus filas; no se infiere a partir de una corrida aislada. No se calcula duracion mensual hasta colapso con este ejecutor.

Los resultados del objetivo antiguo basado en costo no son comparables con este esquema 2. Los resultados experimentales se versionan; mantener separados los historicos y las nuevas campanas. Consultar experimentacion/SIMULACION-COMPARADA.md para simulaciones continuas con ambos motores.

## Verificacion

Las regresiones compartidas comprueban restricciones, inicial comun y reproducibilidad. ExperimentacionTest comprueba ultima parte, fin de servicio, completitud, colapso, ausencia de demanda, prioridad de holgura y exportacion individual/pareada.

## Configuracion experimental v3

Almacen central (27,14), stock ilimitado; Nor-Oeste (12,38) y Este (57,27), stock inicial de 1000 cada uno, repuesto diariamente en la simulacion. La flota inicia en el central. Se mantienen turnos, alimentacion, capacidad, servicio y retorno. Usar - en el antiguo argumento de mantenimiento; no se requiere archivo vacio. No mezclar nuevas campanas con resultados previos a estas coordenadas y a la exclusion de mantenimiento. Las metricas principales siguen siendo Ta y holgura temporal; duracion hasta colapso y cobertura complementan su interpretacion.
