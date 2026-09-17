# Experimentación numérica — TS y ALNS estrictos

Se ejecutaron 12 búsquedas: dos snapshots (01/09/2026 y 01/10/2026, 08:00),
tres semillas (20262, 20263, 20264), TS y ALNS estrictos. Cada búsqueda tiene 20 iteraciones
máximas y desactiva parada por tiempo. No es una simulación de meses completos.

## Condiciones compartidas

Mismo EstadoOperacion inmutable, parámetros, función objetivo, partes, constructor,
evaluador y caminos temporales. La carga lee 5000 pedidos por mes y selecciona 36 por
registro y horizonte de 24 horas. Los demás quedan contabilizados como excluidos,
no se los declara entregados. No se simularon despachos anteriores a las 08:00.

Servicio de 60 minutos, plazo por fin de servicio, turnos 07–15/15–23/23–07,
descanso de 60 minutos en banda [inicio+60,inicio+420]. Partes de hasta cuatro paquetes.
Mantenimiento y bloqueos de los archivos proporcionados.

TS genera hasta 400 candidatos por iteración, tenencia 7.
ALNS destruye hasta cuatro partes y repara con orden por plazo o aleatorio.
Ambos usan estancamiento de 20 iteraciones. Igual número de iteraciones no implica
igual trabajo computacional; se informan candidatos y Ta para interpretarlo.

## Resultados

| Mes | Motor | Pedidos completos | Paquetes pendientes | Rango de costo operativo (S/) | Rango de distancia (km) |
|---|---|---:|---:|---:|---:|
| Septiembre | TS | 29/36 | 27 | 8722–9540 | 1436–1570 |
| Septiembre | ALNS | 29/36 | 27 | 8754–9284 | 1460–1560 |
| Octubre | TS | 26/36 | 46 | 8430–8596 | 1300–1384 |
| Octubre | ALNS | 26/36 | 46 | 8730–8988 | 1344–1424 |

En las doce salidas: rutas factibles y **cobertura incompleta**. No se aceptan entregas
tardías para aumentar artificialmente la cobertura. Las cantidades pendientes se conservan.
Las inserciones iniciales son idénticas dentro de cada mes: 4772 en septiembre, 5528 en octubre.

El costo operativo incluye costo fijo de las unidades y distancia por tarifa.
El objetivo agrega 1 000 000 por paquete pendiente. No confundir ambos.
TS y ALNS no evaluaron el mismo número de candidatos; los resultados no establecen
superioridad estadística. El primer motor de cada proceso también recibe el costo de
calentamiento de la JVM, por lo que Ta no permite una conclusión de velocidad aislada.

## Evidencia y reproducción

- [Septiembre: CSV](resultados/estricto-202609/metricas.csv).
- [Septiembre: entradas y parámetros](resultados/estricto-202609/README.md).
- [Octubre: CSV](resultados/estricto-202610/metricas.csv).
- [Octubre: entradas y parámetros](resultados/estricto-202610/README.md).
- [Comandos y API](../tabu/README.md).

Cada carpeta conserva los detalles por semilla: asignaciones de partes, horarios,
descanso, retorno y caminos con tiempos por arco. Los README generados incluyen
hashes SHA-256 de los tres archivos de entrada.

La auditoría posterior crea un evaluador nuevo para la salida de cada búsqueda.
Las pruebas sintéticas comprueban además repetición por semilla, solución inicial
idéntica, invariantes de estado e imposibilidad de aceptar aspiración infactible.

Para un estudio formal ampliar días/horas, semillas y escenarios; alternar el orden
de motores, calentar la JVM y comparar presupuestos de tiempo o evaluaciones.
Los resultados anteriores de PruebaDatosReales pertenecen al ALNS histórico, con
tardanza blanda y otras reglas: no se mezclan con estos CSV.
