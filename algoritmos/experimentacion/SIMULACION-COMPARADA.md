# Simulacion cronologica comparable de TS y ALNS

Este ejecutor aplica a ambos motores actuales pruebas continuas, limites de ciclos y campanas por semillas. Se ejecuta desde la raiz del repositorio con Java 17, sin Maven. Los CSV, metadatos y figuras experimentales se guardan en Git; los archivos compilados siguen excluidos.

## Compilar y verificar

```powershell
.\algoritmos\compilar.bat -Pruebas
```

Incluye restricciones comunes, experimentacion y pruebas de continuidad para TS y ALNS: entregas parciales, ultima parte, retorno y reutilizacion de vehiculos, repetibilidad, limite de ciclos y colapso. Las pruebas automaticas emplean datos sinteticos y archivos temporales.

## Ejecuciones

En PowerShell, definir las rutas una vez:

```powershell
$ventas = 'algoritmos/alns/data/ventas.v20260909'
$bloqueos = 'algoritmos/alns/data/bloqueos.v20260909'
$mantenimiento = 'algoritmos/alns/data/mant.preventivo.09.10.txt'
```

Prueba corta de ambos motores: 12 ciclos de 10 minutos, 2 iteraciones y semilla 1.

```powershell
java "-Dfile.encoding=UTF-8" -cp algoritmos/out pe.pucp.paqrap.SimulacionComparada AMBOS $ventas $bloqueos $mantenimiento 2026-01 algoritmos/experimentacion/resultados/prueba-corta 2 1 12 10 1
```

Campana oficial de comparacion, hasta el colapso o fin de datos (solo bloqueos y mantenimiento, sin averias), diez semillas:

```powershell
java "-Dfile.encoding=UTF-8" -cp algoritmos/out pe.pucp.paqrap.SimulacionComparada AMBOS $ventas $bloqueos $mantenimiento 2026-01 algoritmos/experimentacion/resultados/campana-colapso 300 20262,20263,20264,20265,20266,20267,20268,20269,20270,20271
```

(`maxCiclos`, `Sa` y `factor` quedan en sus valores por defecto: 0 = sin limite, 10 y 1.)

Variante acotada a 5 dias (`maxCiclos=720` con `Sa=10`, 720 x 10 min = 7200 min), util para comparar bajo un horizonte fijo en vez de duracion hasta el colapso:

```powershell
java "-Dfile.encoding=UTF-8" -cp algoritmos/out pe.pucp.paqrap.SimulacionComparada AMBOS $ventas $bloqueos $mantenimiento 2026-01 algoritmos/experimentacion/resultados/campana-5dias 300 20262,20263,20264 720 10 1
```

Para ALNS o TS solo, usar ese algoritmo y otra carpeta. Para comparacion pareada usar `AMBOS`: ejecuta TS y despues ALNS para cada semilla, restaurando toda la simulacion. La carpeta debe ser nueva; no se sobrescriben corridas.

Argumentos: algoritmo, carpeta de ventas, carpeta de bloqueos, mantenimiento, mes inicial `AAAA-MM`, salida, iteraciones (300), semillas (1,2,3), maximo de ciclos (0), Sa en minutos (10), factor de demanda (1), archivo opcional de averias. Los parentesis indican valores predeterminados. Averias por archivo: una linea `TA01;2026-01-01T08:00;2026-01-01T12:00`, sin cabecera.

### Modo oficial de comparacion: hasta el colapso

`maxCiclos=0` (el valor por defecto) es el modo oficial para comparar TS y ALNS: cada motor corre sobre la misma instancia y semilla hasta que su propio plan de un ciclo quede incompleto (`COLAPSO_PLANIFICACION`) o se agote la demanda (`FIN_DE_DATOS`). La metrica principal es `duracion_dias` en `resumen.csv`: el algoritmo que aguanta mas dias sin colapsar, bajo identica instancia/semilla, es el que mejor generaliza. A diferencia del pipeline historico en R (obsoleto, solo tenia datos de ALNS), este modo es simetrico: TS y ALNS de una misma semilla ven exactamente los mismos bloqueos y mantenimientos.

Use `maxCiclos=720` con `Sa=10` solo cuando se necesite acotar la corrida a una ventana fija de 5 dias (por ejemplo, para limitar tiempo de computo o comparar bajo un horizonte comun cuando ninguno de los dos colapsa). No mezcle resumenes de ambos modos en el mismo analisis: son metricas distintas (duracion hasta colapso vs. desempeno en una ventana fija).

### Averias: no modeladas por defecto

Por indicacion del curso (1INF54, 2026-2), las averias **no** se generan automaticamente: solo se activan si se pasa explicitamente un archivo de averias (`vehiculo;inicio-ISO;fin-ISO`). El modelo oficial del curso define 3 tipos de averia con reglas propias que el `Averia(vehiculo, inicio, fin)` actual no captura del todo (tipo 1: 2 h fuera de servicio; tipo 2: fuera de servicio hasta el final del siguiente turno, la unidad permanece 4 h en el lugar y luego se traslada instantaneamente con su carga no trasvasada al almacen central; tipo 3: al menos 2 dias, retorna en el turno 15:00-23:00, permanece 4 h en el lugar y luego igualmente se traslada con su carga al almacen central). Implementar esas reglas (reubicacion de la unidad y de los paquetes al almacen central) queda pendiente; hasta entonces, las campanas de comparacion corren solo con bloqueos y mantenimiento.

### Bloqueos: nodo bloqueado = cierra toda la interseccion

Un nodo que forma parte de un tramo bloqueado no se puede atravesar ni admite giro lateral: la unica salida es volver por donde se llego (vuelta en U), tal como especifica el curso. `CalculadorRuta` construye el `GridMap` con `bloquearNodos=true`, que cierra las 4 calles incidentes a cada nodo del tramo bloqueado durante todo el intervalo (no solo las aristas de la poligonal); antes solo se cerraban las aristas de la poligonal misma, lo que permitia girar hacia una calle perpendicular no bloqueada en ese mismo nodo. El curso 2026-2 solo entrega poligonos abiertos (siempre se puede llegar a todos los puntos de la poligonal), que es el unico caso que `Bloqueo`/`BloqueoParser` validan hoy.

## Escenarios y reglas

- E1: factor 1 y archivos del escenario normal. Para un escenario sin incidencias, suministrar archivos de bloqueos y mantenimiento vacios.
- E2: repetir con factores crecientes, por ejemplo 1, 1.25, 1.5 y 2; cada cantidad se multiplica y redondea hacia arriba. Usar las mismas semillas y datos en ambos motores.
- E3: suministrar bloqueos, mantenimientos y opcionalmente averias. Se validan mediante el evaluador comun.

Se leen meses consecutivos desde el mes inicial hasta el primer archivo de ventas ausente. Cada mes presente requiere su archivo de bloqueos (puede estar vacio). El inicio es el dia 1 a las 00:00 con flota en el almacen central, sin pedidos ni operaciones heredados del mes anterior. Los meses posteriores mantienen el estado. Cambiar esta condicion inicial requiere suministrar un estado previo real, no asumir entregas anteriores.

Solo ingresan pedidos cuya fecha de registro ya llego; no se anticipan ventas futuras. Sa es externo a TS/ALNS. No se utiliza K/Sc para dar conocimiento del futuro. Se planifica toda la demanda registrada no despachada, sin limite de 400 pedidos ni recorte de deadlines. Las rutas que salen antes del ciclo siguiente se comprometen; otras se vuelven a planificar. Se descuenta inventario al comprometer el despacho y se repone diariamente al stock inicial. Las entregas se registran al terminar el servicio de la ultima parte; los vehiculos quedan disponibles en el destino desde su retorno. Las rutas comprometidas no se deshacen entre ciclos.

El simulador conserva en una agenda externa las entregas en curso y representa cada vehiculo comprometido por su posicion de retorno y disponibilidad futura. No modela desvio en tiempo real de una ruta ya despachada ante una incidencia inesperada; las incidencias cargadas son conocidas al planificar. Las reglas operativas son las actuales de los motores compartidos (incluido servicio de 60 minutos), no las del ALNS historico.

Cada ruta debe regresar antes de finalizar el turno en que sale. Cuando ya no cabe en el turno vigente, el evaluador prueba los inicios de los turnos posteriores hasta el deadline mas temprano de la ruta. El descanso realizado solo se conserva para el turno vigente; una ruta programada en un turno futuro debe incluir nuevamente su descanso. Esto evita declarar colapso por un pedido que puede esperar al siguiente relevo sin vencer.

Alimentacion: el INICIO permitido es 08:00-14:00, 16:00-22:00 o 00:00-06:00 segun el turno, con 60 minutos completos y fin no posterior al cambio de turno. El simulador acredita tambien 60 minutos continuos sin rutas desde el ultimo retorno (o arranque), dentro de esa banda. Es una politica comun de alimentacion oportunista durante inactividad; una pausa interrumpida no se suma a otra. Una pausa incluida en una ruta se acredita al llegar su hora final, no en el momento de programarla. No se hereda al siguiente turno.

`COLAPSO_PLANIFICACION` significa que el motor no encontro plan completo para la demanda del ciclo; no prueba imposibilidad matematica. Un resultado invalido lanza un error, no se registra como colapso. `FIN_DE_DATOS` espera entregas y retornos; `LIMITE_DE_CICLOS` indica truncamiento y no debe contarse como colapso.

## Salidas e interpretacion

- `TS-semilla.csv` / `ALNS-semilla.csv`: Ta por llamada, holgura media/minima del plan completo, distancia, tiempo de rutas, vehiculos, utilizacion, pendientes y entregas acumuladas. Las metricas del plan incluyen rutas aun no despachadas; no sumar esas distancias como recorrido real.
- `resumen.csv`: fin, duracion simulada, pedidos/paquetes entregados, holgura real de pedidos completados, Ta total/medio/maximo y metricas de rutas comprometidas. La distancia despachada incluye el recorrido comprometido aun pendiente al detenerse la corrida. Utilizacion = carga despachada / capacidad acumulada de las salidas, no ocupacion media temporal.
- `metadatos.txt`: argumentos, parametros, Java y hash de la instancia procesada. Guardar junto a estos archivos el commit de Git y si habia cambios locales.

La holgura real al colapsar solo describe pedidos completados, no atribuye cero a pendientes. Compararla sin considerar cobertura y duracion puede sesgar el estudio. Ta excluye carga de archivos y exportacion. El tiempo real varia entre repeticiones aunque la semilla sea igual. Los motores reinician su generador con la semilla configurada en cada llamada, como en los ejecutores existentes.

Ambas trayectorias parten de la misma demanda, flota, parametros y semilla; despues divergen por sus decisiones. Para comparar exactamente el mismo EstadoOperacion usar `CompararAlgoritmos`, descrito en la guia principal. Igual numero de iteraciones no implica igual esfuerzo computacional. Para medir Ta rigurosamente controlar calentamiento JVM, orden de ejecucion y entorno; la prueba corta es de funcionamiento.

No combinar estas corridas con los CSV historicos importados de `alns/simulacion-colapso`: utilizan otro modelo y otras reglas. El script R historico tampoco analiza automaticamente este nuevo esquema; conservar los resultados por campana para el analisis posterior.
