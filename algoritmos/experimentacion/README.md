# Experimentación numérica: duración hasta el colapso

Compara ALNS y Búsqueda Tabú por **cuánto dura la simulación hasta el colapso**: el algoritmo
que dura más es mejor. La métrica por defecto es `dias_simulados`, el tiempo simulado desde el
inicio (2026-01-01 00:00) hasta el colapso. Como métricas operativas complementarias, el CSV de
ALNS incluye el tiempo total de planificación (`tiempo_planificador_s`) y la holgura promedio de
entrega (`holgura_promedio_min`).

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
una fila por corrida y un archivo por corrida o un único CSV. Para comparar las métricas
secundarias del diseño experimental, debe agregar también `tiempo_planificador_s` y
`holgura_promedio_min`. Opcionales: `algoritmo`, `semilla`, `escenario` y los parámetros de la
configuración. Para que la comparación sea válida, Tabú debe simularse con el mismo escenario: el
mismo mes inicial, Sa y K, despacho progresivo, meses encadenados y el mismo criterio de colapso.

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
