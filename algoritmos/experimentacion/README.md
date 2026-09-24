# Experimentacion pareada por EstadoOperacion

Framework oficial de comparacion TS vs ALNS: [SimulacionComparada](SIMULACION-COMPARADA.md), sembrado y corriendo por defecto hasta el colapso o fin de datos (`maxCiclos=0`; use `maxCiclos=720` con `Sa=10` para una ventana acotada de 5 dias). Es el unico framework simetrico entre ambos motores (misma instancia y mismos bloqueos para TS y ALNS de cada semilla; averias y mantenimiento excluidos, ver nota en [SIMULACION-COMPARADA.md](SIMULACION-COMPARADA.md#incidencias-excluidas-de-experimentacion)) y sus resultados se versionan en Git.

La ejecucion vigente TS/ALNS, las metricas de Ta y holgura, los CSV individuales y el protocolo de colapso de planificacion se describen en la [guia comun](../README.md) y los [metadatos](../METADATOS-PRUEBAS.md). Ambos motores usan la misma instancia y semillas. No mezclar estos CSV con el script R historico descrito abajo.

Para el analisis estadistico de una campana de `SimulacionComparada` (comparacion **pareada** por semilla, ya que TS y ALNS de una misma semilla comparten instancia), use `analisis_colapso_pareado.py` (Python; no requiere R):

```bash
python analisis_colapso_pareado.py --entrada resultados/campana-colapso --salida resultados/campana-colapso/analisis
```

Ver la cabecera del script para las opciones (`--metrica`, `--alfa`, `--delta`, `--alternativa`, `--incluir-censuradas`).

## Referencia historica (obsoleta para la comparacion actual): duracion hasta el colapso

**No usar este pipeline para comparar TS y ALNS.** El contenido siguiente pertenece al simulador historico de ALNS (`alns/src/pe/pucp/paqrap/simulacion/Simulador.java`, obsoleto); TS estricto nunca se conecto a ese simulador y no existen corridas de Tabu equivalentes (`alns/resultados/experimentos/alns/*` solo tiene datos de ALNS). Sus hipotesis, scripts (`experimentos_colapso.*`, `analisis_colapso.R`) y comandos quedan documentados por trazabilidad, pero la comparacion pareada vigente usa exclusivamente `SimulacionComparada` (arriba). El modo "hasta el colapso" (duracion abierta) sigue disponible ahi con `maxCiclos=0` si se necesita esa metrica dentro del framework nuevo y simetrico.

Compara ALNS y Búsqueda Tabú por **cuánto dura la simulación hasta el colapso**: el algoritmo
que dura más es mejor. La métrica por defecto es `dias_simulados`, el tiempo simulado desde el
inicio (2026-01-01 00:00) hasta el colapso.

## Hipótesis

| | |
|---|---|
| H0 | μ_Tabú = μ_ALNS: Tabú no tiene diferencia con ALNS |
| H1 | μ_Tabú > μ_ALNS: Tabú tiene mejor desempeño (dura más hasta el colapso) |

Prueba unilateral con α = 0,05. La unidad de muestra es una corrida completa hasta el colapso;
las corridas de un algoritmo difieren solo en la semilla, así que son independientes.

## 1. Corridas de ALNS

Desde `algoritmos/alns`:

```bash
./experimentos_colapso.sh 30                    # 30 semillas, 300 iteraciones, en paralelo
./experimentos_colapso.sh 30 --iteraciones 50   # otra configuración
DESTINO=resultados/experimentos/alns_it50 ./experimentos_colapso.sh 30 --iteraciones 50
```

```bat
experimentos_colapso.bat 30
```

- Cada corrida deja `alns_semilla_<s>.csv` (una fila con el resumen) y `alns_semilla_<s>.txt`
  (la salida completa) en `resultados/experimentos/alns/`, o en `DESTINO`.
- Las semillas que ya tienen CSV se omiten: el script se puede relanzar para completar una
  campaña interrumpida o ampliarla subiendo N.
- Use un `DESTINO` distinto para cada configuración (iteraciones, Sa, K). El análisis avisa si
  una muestra mezcla configuraciones.
- Variables: `PARALELO` (corridas simultáneas; por defecto la mitad de los núcleos),
  `SEMILLA_BASE`, `MES`, `JAVA_OPTS`.
- Correr en paralelo alarga el tiempo real de cada corrida, pero no cambia los días simulados,
  que son la métrica comparada.

## 2. Corridas de Tabú

Deben producir CSV con, al menos, las columnas `fin` (`COLAPSO` si colapsó) y `dias_simulados`,
una fila por corrida y un archivo por corrida o un único CSV. Opcionales: `algoritmo`,
`semilla`, `escenario` y los parámetros de la configuración. Para que la comparación sea válida,
Tabú debe simularse con el mismo escenario: el mismo mes inicial, Sa y K, despacho progresivo,
meses encadenados y el mismo criterio de colapso.

## 3. Análisis en R

```bash
Rscript analisis_colapso.R --alns ../alns/resultados/experimentos/alns
Rscript analisis_colapso.R --alns ../alns/resultados/experimentos/alns \
                           --tabu ../tabu/resultados/experimentos/tabu
```

Opciones: `--metrica` (columna a comparar, por defecto `dias_simulados`), `--alfa` (0.05),
`--delta` (diferencia de interés en días para el tamaño de muestra, 1) y `--salida` (carpeta de
resultados, por defecto `experimentacion/resultados`). Usa solo R base y `survival`, que viene
con R.

Procedimiento del script:

1. Descriptivos por algoritmo (n, media, desviación, coeficiente de variación, cuartiles).
2. Supuestos: Shapiro-Wilk por muestra y Fligner-Killeen para varianzas.
3. Prueba principal unilateral: **t de Welch** si ambas muestras son compatibles con normalidad;
   si no, **Wilcoxon-Mann-Whitney**. La otra se reporta como complementaria.
4. Tamaño del efecto: g de Hedges y P(Tabú dura más que ALNS).
5. Potencia alcanzada y N por grupo para detectar `--delta` días con potencia 0,80.
6. Si hay corridas que terminaron sin colapsar, se advierte (su duración es censurada) y se
   agrega la prueba log-rank.
7. Decisión: se rechaza o no H0.

Con solo `--alns` produce descriptivos, normalidad, gráficos y el **N sugerido por grupo** a
partir de la dispersión observada: sirve para fijar N antes de correr Tabú.

Salida: `informe_colapso.txt`, `resumen_descriptivo.csv`, `boxplot_colapso.png`,
`supervivencia_colapso.png` (fracción de corridas sin colapsar a lo largo del tiempo) y
`qqplot_colapso.png`.

## Configuracion experimental v3

Almacen central (27,14), stock ilimitado; Nor-Oeste (12,38) y Este (57,27), stock inicial de 1000 cada uno, repuesto diariamente en la simulacion. La flota inicia en el central. Se mantienen turnos, alimentacion, capacidad, servicio y retorno. Usar - en el antiguo argumento de mantenimiento; no se requiere archivo vacio. No mezclar nuevas campanas con resultados previos a estas coordenadas y a la exclusion de mantenimiento. Las metricas principales siguen siendo Ta y holgura temporal; duracion hasta colapso y cobertura complementan su interpretacion.
