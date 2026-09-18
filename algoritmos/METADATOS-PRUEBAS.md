# Metadatos de la comparación común

## Qué se compara

ALNS adaptado y TS utilizan el mismo `EstadoOperacion`, `ParametrosOperacion`, `GeneradorSolucionInicial`, `EvaluadorFactibilidad`, `CalculadorRuta` y `PathFinder`. Las pruebas verifican la igualdad de las soluciones con cero iteraciones y la reproducibilidad por semilla.

El ALNS histórico de `algorithms` conserva su arnés mensual independiente y no forma parte de estos resultados. Su selector adaptativo y aceptación se reutilizan en la adaptación experimental. Las diferencias de exploración son deliberadas: TS usa vecindarios y memoria tabú; ALNS destruye y repara partes.

## Modelo compartido

- Entrada: instante, pedidos pendientes, vehículos, almacenes, bloqueos, averías, mantenimientos, rutas en curso y descansos realizados.
- Carga CLI: pedidos registrados hasta el instante, deadline como máximo T+24h, orden por plazo y límite de 400. No descuenta entregas de ejecuciones anteriores.
- Servicio de 60 minutos; por defecto el plazo incluye su finalización.
- Turnos de 8 horas con origen 07:00, retorno dentro del turno y descanso de 60 minutos dentro de la banda relativa [60,420].
- Velocidades TA=40, TM=25, TB=12 km/h; almacenes central (25,15), NO (12,38), este (55,27).
- Partes predefinidas de hasta 4 unidades; inventario agregado e integridad de cantidades.
- Objetivo común = costo operativo + 1 000 000 × paquetes pendientes. El costo operativo incluye S/50 por vehículo usado.
- Rutas asignadas factibles pueden coexistir con demanda pendiente: `factible` y `completa` se reportan por separado.

Las reglas son las del núcleo estricto actual; comparabilidad no significa cumplimiento de todas las propuestas del informe. No se implementaron nuevos intercambios de cantidades ni reoptimización de sufijos de rutas activas. El cargador CLI no genera averías ni rutas en curso; esos casos se inyectan desde Java y se ejercitan en las regresiones.

## Parámetros y protocolo

TS: tenencia 7, 400 candidatos por iteración. ALNS: destrucción máxima 4 partes, segmento 5, reacción 0.7, temperatura inicial igual al 5% del costo operativo inicial (mínimo 1); enfriamiento hasta el 1% en el límite de iteraciones. La penalización por pendientes no infla la temperatura.

ALNS dispone de destrucción aleatoria y por cercanía, y reparación por inserción con orden por plazo o aleatorio. La segunda reparación no es arrepentimiento. Los dos motores parten del mismo constructor determinista.

La ejecución individual acepta iteraciones, semilla y presupuesto en ms. Cero ms desactiva el reloj. El experimento conjunto registra calentamiento y alterna el orden entre semillas. Comparar con un presupuesto temporal común y un límite de iteraciones suficientemente alto; revisar el tiempo realmente consumido. Los límites son cooperativos, no interrupciones estrictas. Con reloj la trayectoria puede cambiar entre equipos aun usando la misma semilla.

El contador de candidatos no mide lo mismo: TS cuenta vecinos y ALNS evaluaciones realizadas durante reparación. No usarlo como un presupuesto homogéneo. Igual número de iteraciones tampoco equivale a igual esfuerzo.

## Archivos y trazabilidad

Las entradas siguen los formatos:
- Ventas: `DDdHHhMMm:x,y,cCliente,cantidad,plazoHoras`.
- Bloqueos: `DDdHHhMMm-DDdHHhMMm:x1,y1,...`.
- Mantenimiento: `AAAAMMDD:unidad`.

Cada experimento guarda hashes SHA-256 de las tres entradas, instante, conteos de selección/exclusión, parámetros, presupuesto, semillas, objetivo inicial, Java, SO y procesadores. Para identificar el código registrar también `git rev-parse HEAD` y `git status --short`; el informe generado no captura Git automáticamente.

No sobrescribe directorios con resultados existentes. Los CSV contienen costo, objetivo, cobertura, pendientes, km, tiempos, utilización, iteraciones, factibilidad y parada. Los informes por semilla incluyen caminos y horarios para auditoría.

## Validación realizada

- 15 grupos de pruebas compartidas: memoria tabú, aspiración, división, plazo, turno, descanso, mantenimiento, avería, stock, bloqueo, integridad, vecindarios, replanificación, comparabilidad y validación de entrada.
- [Septiembre, 20 iteraciones](experimentacion/resultados/comparable-septiembre/README.md): 3 semillas por motor; 36 pedidos considerados; 29 completos y 27 paquetes pendientes.
- [Octubre, 20 iteraciones](experimentacion/resultados/comparable-octubre/README.md): 3 semillas por motor; 36 pedidos considerados; 26 completos y 46 paquetes pendientes.
- [Septiembre, presupuesto 1000 ms](experimentacion/resultados/comparable-tiempo/README.md): 3 semillas por motor, límite alto de iteraciones; salida por tiempo y rutas factibles. El tiempo observado refleja el pequeño exceso de una operación en curso.

Son 18 ejecuciones reales auditadas. Todas mantienen o mejoran el objetivo inicial. Estas muestras verifican la comparación y no prueban superioridad estadística ni cobertura mensual completa.
