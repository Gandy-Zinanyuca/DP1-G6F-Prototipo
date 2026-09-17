# Pruebas con los archivos proporcionados

Se extrajeron únicamente septiembre y octubre de 2026 a algoritmos/datos-locales/,
carpeta ignorada por Git. Se copió allí mant.preventivo.09.10.txt sin modificar el original.
Fuentes: ventas.v20260909.zip y bloqueos.v20260909.zip de Downloads.
Los registros de carga reflejan los parsers actuales, no una auditoría exhaustiva del dataset.

## Alcance

Dos ciclos independientes: día 1, 08:00, uno por mes. No son simulaciones mensuales,
ni un estudio estadístico, ni una comparación con Tabu Search (todavía pendiente).
Se cargan las 5000 ventas de cada mes; el filtro de registro/horizonte selecciona
36 pedidos por ciclo. Horizonte de 24 horas y máximo de 400 pedidos.
Flota inicial: 10 autos, 15 motos y 12 bicicletas. Almacenes intermedios: 1000 unidades.
Semilla 20262, 200 iteraciones, sin parada por tiempo; cada caso se ejecuta dos veces.
La prueba infiere el período del nombre de ventas y exige bloqueos del mismo mes.
La demo histórica sigue fijando enero de 2026: utilizar PruebaDatosReales para estos casos.

| Resultado | Septiembre 2026 | Octubre 2026 |
|---|---:|---:|
| Ventas cargadas / omitidas | 5000 / 0 | 5000 / 0 |
| Bloqueos cargados / omitidos | 558 / 0 | 622 / 0 |
| Registros de mantenimiento cargados | 37 | 37 |
| Unidades asignables | 36 | 37 |
| Mantenimiento vigente del día | TA01 | Ninguno |
| Pedidos asignados / diferidos | 36 / 0 | 36 / 0 |
| Entregas tardías | 3 | 8 |
| Costo objetivo inicial | 613276 | 1620268 |
| Costo objetivo ALNS | 610516 | 1618512 |
| Distancia (km) | 826 | 1014 |
| Costo operativo (soles) | 6036 | 7522 |
| Unidades utilizadas | 11 | 14 |
| Tiempo primera búsqueda (ms, observado) | 146 | 113 |

El costo objetivo incluye penalizaciones; no es el costo monetario de operación.
Los tiempos dependen del equipo y no incluyen carga de archivos ni la segunda ejecución.

Ambos casos pasan integridad de pedidos, capacidad, inventario, disponibilidad de
unidades, no empeoramiento frente al constructor y repetición de la solución con semilla fija.
Ambas soluciones son **no admisibles** por tardanza. Pasar estos controles no implica
cumplimiento de todas las restricciones del cuadro: véase VERIFICACION-ALNS.md.
Los pedidos anteriores a las 08:00 se consideran pendientes; no se simularon despachos
previos, lo que también limita la interpretación de las tardanzas.

## Repetir

Desde la raíz del repositorio, después de ejecutar algoritmos/compilar.bat
(o bash algoritmos/compilar.sh):

```sh
java -cp algoritmos/out pe.pucp.paqrap.PruebaDatosReales algoritmos/datos-locales/ventas.202609.txt algoritmos/datos-locales/bloqueo.2609.txt algoritmos/datos-locales/mant.preventivo.09.10.txt 1 8
java -cp algoritmos/out pe.pucp.paqrap.PruebaDatosReales algoritmos/datos-locales/ventas.202610.txt algoritmos/datos-locales/bloqueo.2610.txt algoritmos/datos-locales/mant.preventivo.09.10.txt 1 8
```

La prueba termina con error ante archivos de meses distintos, registros de ventas/bloqueos
omitidos, ciclo sin pedidos o fallo de las invariantes. Reporta la inadmisibilidad sin
confundirla con un fallo de integridad. Las tres pruebas sintéticas del compilador
también pasaron tras incorporar este ejecutable.
