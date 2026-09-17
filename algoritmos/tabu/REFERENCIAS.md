# Referencias publicadas del 09/09/2026

Fuente: `datos/referencias/c.1inf54.26-2.preguntas.respuestas_PUBLICADO.xlsx`. El Excel se leyó con la habilidad de hojas de cálculo y se preservó sin modificaciones. Las instrucciones sobre otros entregables del curso se distinguen del encargo de actualizar este planificador Tabu Search.

| Fuente | Valor adoptado | Aplicación |
|---|---|---|
| Flota!B3:D5 | 10 autos, 15 motos, 12 bicicletas; capacidades 24, 8, 4; velocidades 20, 40, 14 km/h | ReferenciaProyecto y ParametrosOperacion.publicados |
| Flota!G3:G5 y encargo original | 8, 6, 3 S/km | Costos conservados |
| PR_Proyecto!D19; Mapa!L58:M60 | Central (27,14), NO (12,38), Este (57,27) | Salida/regreso al central; intermedios como referencia |
| PR_Proyecto!B18:B19 y D18:D19 | D19 reemplaza las coordenadas antiguas | Actualización del 08/09/2026 23:55 |
| PR_Proyecto!D27 | cIdCliente identifica al cliente | Campo clienteId separado del ID de pedido por mes/línea |
| PR_Proyecto!D36 y D45 | El plazo excluye la hora de acondicionamiento | Deadline en la llegada, servicio de 60 minutos antes de continuar |
| PR_Proyecto!D21 y D48:D51 | Velocidades por tipo aplicables a la siguiente planificación | Instantánea inmutable por llamada, configurable por archivo/CLI |
| PR_Proyecto!D24:E24 | No atravesar ni girar por nodos bloqueados | Cierre conservador de todas sus calles incidentes |
| PR_Proyecto!D60:G60 | Mantenimiento de día completo, repetido cada dos meses, 2026–2029 | 888 fechas derivadas del TXT original; validación hasta el regreso |
| Guía!B25:B27 y encargo original | Ta, Sa y Sc | El Excel no prescribe Sa=30, K=4, iteraciones ni tenencia |

## Discrepancias resueltas

- `Flota!F38:F46` discrepa del TXT y de la columna H. La fuente operativa es **mant.preventivo.09.10.txt**, coincidente con `PR_Proyecto!D60:G60` y `Flota!H10:H46`. Por ejemplo, TM07 tiene mantenimiento el 03/09, no el 02/09; TM09 el 09/09, no el 08/09.
- El origen geométrico sigue en (0,0). La posición del depósito se actualiza a (27,14).
- Las velocidades 40/25/12 y el deadline al terminar el servicio se conservan solo en la demo legada. El perfil publicado utiliza 20/40/14 y deadline en la llegada.
- Las estimaciones de viajes/carga diaria en `Flota!E3:F7` no son capacidades por ruta ni obligaciones de viajar varias veces en esta ejecución.

## Datos y trazabilidad

| Insumo | Archivos/registros | Cobertura |
|---|---:|---|
| ventas.v20260909.zip | 36 TXT / 160,010 pedidos | Enero 2026–diciembre 2028 |
| bloqueos.v20260909.zip | 36 TXT / 21,725 intervalos | Enero 2026–diciembre 2028 |
| mant.preventivo.09.10.txt | 37 registros originales | Septiembre–octubre 2026 |
| Patrón de mantenimiento expandido | 888 fechas derivadas | Enero 2026–diciembre 2029 |

Los TXT conservan sus bytes originales. `datos/inventario.json` registra nombre, cantidad de registros y SHA-256 por archivo mensual. Se excluye desktop.ini. No se inventan ventas o bloqueos de 2029.

La presencia de un archivo mensual no significa que haya ventas todos sus días: el último registro de septiembre de 2026 es del día 29 a las 23:50. No se generan pedidos para rellenar el día 30.

`ventas.YYYYMM.txt` usa año completo; `bloqueo.AAMM.txt`, año 2000+AA. El cargador incluye los meses de ventas atravesados por [T,T+Sc] y todos los bloqueos conocidos que no hayan terminado en T, incluso los anteriores a T y los posteriores a Sc.

`V202609-L01454` identifica la línea física 1454 de septiembre de 2026. Es estable mientras el archivo no cambie. El código de cliente se conserva y muestra por separado. Incluso dos líneas idénticas se conservan como pedidos distintos: el insumo no ofrece un ID de pedido con el que demostrar una duplicación accidental.

El cierre de nodos es conservador: todo el cruce de una calle incidente debe quedar fuera del bloqueo de cualquiera de sus extremos. Puede producir mayor rodeo o espera que una interpretación menos restrictiva. El plan evita programar pasos por intersecciones bloqueadas; no simula la maniobra reactiva de vuelta en U. Se permite esperar a la reapertura.

## Alcance de la actualización

Los documentos también describen GUI, stock, recargas, viajes múltiples, entregas parciales, averías y trasvases. No se implementan automáticamente como parte de actualizar datos y referencias de parámetros. Se mantiene una ruta cerrada por vehículo, pedidos indivisibles, restricciones duras y Tabu Search como única metaheurística. Los almacenes intermedios se registran como referencias, sin recarga operativa en este MVP.
