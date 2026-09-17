# Metadatos y pruebas reproducibles

## Procedencia

Integración selectiva sobre `algorithms` (`50f317c`), con TS y núcleo estricto de `dev/yaser` (`6864d6a`). Las fuentes de ambos motores se conservan; cambian la organización de la ejecución, las pruebas de integración y las guías. La suite de TS procede de `RestriccionesEstrictasTest` de `dev/yaser`, adaptada para probar solamente TS, sin traer un segundo ALNS.

## Datos de entrada

| Archivo | Contenido y formato |
| --- | --- |
| `ventas.AAAAMM.txt` | `DDdHHhMMm:x,y,cCliente,cantidad,plazoHoras` |
| `bloqueo.AAMM.txt` | `DDdHHhMMm-DDdHHhMMm:x1,y1,x2,y2,...` |
| `mant.preventivo.09.10.txt` | `AAAAMMDD:codigoVehiculo` |

Las carpetas bajo `alns/data` incluyen 36 meses de ventas y bloqueos, desde enero de 2026 hasta diciembre de 2028. El mantenimiento contiene 37 registros de septiembre/octubre de 2026; estos lanzadores no deben interpretarse como una expansión automática del mantenimiento para todos los años. El código de cliente puede repetirse: no es una clave única de venta.

## Metadatos que deben acompañar cada experimento

- Commit y rama: `git rev-parse HEAD` y `git branch --show-current`. Registrar también si hay cambios locales (`git status --short`).
- Motor utilizado, comando completo y parámetros, incluida la semilla.
- Archivos y hashes SHA-256, obtenidos con `Get-FileHash -Algorithm SHA256 <archivo>`.
- Fecha/hora simulada, mes, horizonte y límite de selección; en ALNS, ciclos, Sa y K.
- Pedidos leídos, considerados y excluidos. No interpretar el total mensual leído como demanda efectivamente planificada.
- JDK (`java -version`), equipo y presupuesto de ejecución.
- Costo, distancia, cobertura, cantidades pendientes, factibilidad, tiempo y motivo de parada.

Los lanzadores muestran parte de estos datos en consola; no generan automáticamente un manifiesto con hashes y versión Git. Guardar la salida en un archivo distinto por experimento. La semilla hace repetible la secuencia aleatoria con el mismo código, entradas y parámetros; el tiempo real puede variar.

## Diferencias vigentes entre los motores

| Aspecto | ALNS de algorithms | TS de dev/yaser |
| --- | --- | --- |
| Entrada del motor | `ContextoPlanificacion` y plan previo | `EstadoOperacion` y `ParametrosOperacion` |
| Selección temporal | Usa Sa/K/Sc y contexto de ciclos | El adaptador selecciona pedidos; el motor no filtra por Sa/K/Sc |
| Solución inicial | Constructor propio de ALNS | Constructor del núcleo estricto |
| Evaluación | `Solucion` y `Ruta` de ALNS | `EvaluadorFactibilidad` y `CalculadorRuta` |
| Cantidades | Modelo `Pedido` del ALNS conservado | Partes predefinidas, normalmente de hasta 4 unidades |
| Cobertura | Inicial incompleta puede detener la búsqueda y reportar colapso | Pendientes explícitos; rutas asignadas pueden ser factibles con cobertura incompleta |
| Objetivo | Distancia por costo/km | Costo operativo, costo fijo por vehículo y penalización por paquete pendiente |
| Turno | `limitarRutaAlTurno=false` por defecto | Retorno dentro del turno de 8 horas; referencia inicial 07:00 |
| Ejecución | Arnés de varios ciclos | Una fotografía, una ruta por vehículo |

TS utiliza por defecto servicio de 60 minutos, plazo incluyendo servicio, descanso de 60 minutos dentro de la banda relativa [60, 420] del turno, costo fijo de S/50 por vehículo y penalización de 1 000 000 por paquete pendiente. Las velocidades son TA=40, TM=25 y TB=12 km/h. El objetivo penalizado no es el costo monetario operativo mostrado al usuario.

Los cambios del informe (un único EstadoOperacion, un evaluador común para ambos y gestión temporal exclusivamente externa) son una evolución pendiente. No se relajaron reglas para hacer coincidir resultados ni se reemplazó el ALNS consolidado. Antes de comparar calidad entre motores hay que unificar lote, disponibilidad, solución inicial y todas las reglas de evaluación. Un mismo número de iteraciones tampoco equivale al mismo trabajo.

## Verificación de esta integración

Comandos principales en [README](README.md). Resultados obtenidos durante la integración:

| Prueba | Resultado |
| --- | --- |
| Compilación JDK 17, classpaths separados | Ambos motores compilan |
| Regresiones TS | 15 grupos aprobados: memoria, aspiración, división, plazos, turnos, descanso, mantenimiento, avería, stock, bloqueos, integridad, vecindarios, replanificación, reproducibilidad y validación |
| ALNS, enero día 1 a las 01:00, 20 iteraciones | 1 pedido; solución factible; S/150; 20 candidatos; verificaciones de costo, integridad, operadores y semilla aprobadas |
| ALNS, enero día 3 a las 10:00, 20 iteraciones | 31 pedidos; inicial no factible; el arnés verifica la devolución sin iterar. Esto no demuestra imposibilidad matemática ni calidad de búsqueda |
| TS, septiembre día 1 a las 08:00, 20 iteraciones, semilla 20262 | 5000 pedidos leídos; 36 considerados; 29 completos; 27 paquetes pendientes; 1570 km; S/9540; rutas factibles y demanda incompleta |

Son pruebas de integración y regresión, no evidencia de superioridad de un motor ni de ejecución de toda la demanda mensual.
