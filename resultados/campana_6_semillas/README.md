# Campaña ALNS hasta el colapso — 6 semillas

Copia de los resultados de la primera campaña de experimentación numérica.

- **Ejecución:** lanzada el 2026-09-22 21:03, resultados subidos el 2026-09-23 10:15
  (commits `8758e25` y `a5875fd`, rama `alns/simulacion-colapso`).
- **Configuración:** semillas 1–6, 300 iteraciones, Sa = 10 min, K = 7, modo `--colapso`,
  arranque 2026-01-01 00:00 con meses encadenados.
- **Modelo:** el anterior a las rutas con recarga (un viaje por ruta, sin hora de alimentación por
  turno ni inventario por día). No es comparable directamente con el modelo actual.
- **Nota:** se lanzó con N = 30 y se redujo a N = 6; la laptop estuvo suspendida durante la noche,
  pero los tiempos registrados no la incluyen (ver `campana_alns_it300.txt`).

| Semilla | Colapso | Días simulados | Tiempo real | Ta promedio | Entregados |
|---|---|---|---|---|---|
| 1 | 2027-01-05 09:40 | 369,4 | 94,1 min | 116 ms | 41 090 |
| 2 | 2027-01-05 18:10 | 369,8 | 94,0 min | 116 ms | 41 183 |
| 3 | 2027-01-05 15:20 | 369,6 | 93,3 min | 115 ms | 41 155 |
| 4 | 2027-01-02 13:00 | 366,5 | 91,0 min | 113 ms | 40 362 |
| 5 | 2027-01-05 15:30 | 369,6 | 93,5 min | 115 ms | 41 157 |
| 6 | 2027-01-05 10:20 | 369,4 | 92,5 min | 114 ms | 41 100 |

## Contenido

- `logs/alns_semilla_N.txt` — salida completa de cada corrida (una línea por día simulado y el
  diagnóstico del colapso).
- `logs/alns_semilla_N.csv` — resumen de la corrida (una fila).
- `campana_alns_it300.txt` — historial de la campaña.
- `analisis_R/` — análisis estadístico en R (`algoritmos/experimentacion/analisis_colapso.R`):
  resumen descriptivo, informe, boxplot, Q-Q plot y curva de supervivencia.

Originales en `algoritmos/alns/resultados/experimentos/` y `algoritmos/experimentacion/resultados/`.
